package dev.pam.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dev.pam.nativeapp.modules.ModuleCompletion
import dev.pam.nativeapp.modules.ModuleResultStatus
import dev.pam.nativeapp.modules.NativeModule
import dev.pam.nativeapp.protocol.WireMap
import dev.pam.nativeapp.protocol.WireValue
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class AuthVaultModule(context: Context) : NativeModule {
    private val preferences = context.applicationContext.getSharedPreferences("dev.pam.auth.vault", Context.MODE_PRIVATE)
    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }

    override fun invoke(method: String, payload: ByteArray, completion: ModuleCompletion) {
        runCatching {
            val values = WireMap.decode(payload)
            when (method) {
                "store" -> store(values.text("key"), values.text("secret"))
                "retrieve" -> retrieve(values.text("key"))
                "delete" -> delete(values.text("key"))
                else -> error("Unknown method: $method")
            }
        }.onSuccess { completion.success(it) }
            .onFailure { completion.failure(it) }
    }

    private fun store(key: String, secret: String): Map<String, WireValue> {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey())
        val ciphertext = cipher.doFinal(secret.toByteArray(Charsets.UTF_8))
        val encoded = Base64.encodeToString(cipher.iv + ciphertext, Base64.NO_WRAP)
        check(preferences.edit().putString(storageKey(key), encoded).commit()) { "Credential could not be persisted" }
        return mapOf("state" to WireValue.Integer(1))
    }

    private fun retrieve(key: String): Map<String, WireValue> {
        val encoded = preferences.getString(storageKey(key), null)
            ?: return mapOf("state" to WireValue.Integer(2))
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        require(bytes.size > 12) { "Credential payload is invalid" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, encryptionKey(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        val secret = cipher.doFinal(bytes.copyOfRange(12, bytes.size)).toString(Charsets.UTF_8)
        return mapOf("state" to WireValue.Integer(1), "secret" to WireValue.Text(secret))
    }

    private fun delete(key: String): Map<String, WireValue> {
        val storageKey = storageKey(key)
        if (!preferences.contains(storageKey)) return mapOf("state" to WireValue.Integer(2))
        check(preferences.edit().remove(storageKey).commit()) { "Credential could not be deleted" }
        return mapOf("state" to WireValue.Integer(1))
    }

    private fun encryptionKey(): SecretKey {
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            generateKey()
        }
    }

    private fun storageKey(key: String) = Base64.encodeToString(key.toByteArray(Charsets.UTF_8), Base64.NO_WRAP or Base64.URL_SAFE)
    private fun Map<String, WireValue>.text(key: String) = (get(key) as? WireValue.Text)?.value ?: error("$key is required")
    private fun ModuleCompletion.success(values: Map<String, WireValue>) = complete(ModuleResultStatus.SUCCESS, WireMap.encode(values))
    private fun ModuleCompletion.failure(error: Throwable) = complete(ModuleResultStatus.FAILURE, (error.message ?: "Auth vault failure").toByteArray())

    private companion object { const val KEY_ALIAS = "dev.pam.auth.vault.key.v1" }
}
