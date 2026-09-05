package dev.pam.auth

import android.content.Context
import android.util.Base64
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pam.nativeapp.modules.ModuleResultStatus
import dev.pam.nativeapp.protocol.WireMap
import dev.pam.nativeapp.protocol.WireValue
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuthVaultModuleTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val key = "test.session"
    private fun invoke(module: AuthVaultModule, method: String, secret: String? = null): Pair<ModuleResultStatus, ByteArray> {
        val values = mutableMapOf<String, WireValue>("key" to WireValue.Text(key), "accessibility" to WireValue.Integer(3))
        secret?.let { values["secret"] = WireValue.Text(it) }
        var result: Pair<ModuleResultStatus, ByteArray>? = null
        module.invoke(method, WireMap.encode(values)) { status, payload -> result = status to payload }
        return requireNotNull(result)
    }

    @Test
    fun persistsEncryptedAcrossModuleInstancesAndDeletes() {
        val secret = "test-only-session-" + System.nanoTime()
        val first = AuthVaultModule(context)
        assertEquals(ModuleResultStatus.SUCCESS, invoke(first, "store", secret).first)
        val preferences = context.getSharedPreferences("dev.pam.auth.vault", Context.MODE_PRIVATE)
        assertFalse(preferences.all.values.any { it.toString().contains(secret) })
        val second = AuthVaultModule(context)
        val recovered = invoke(second, "retrieve")
        assertEquals(ModuleResultStatus.SUCCESS, recovered.first)
        assertEquals(WireValue.Text(secret), WireMap.decode(recovered.second)["secret"])
        assertEquals(ModuleResultStatus.SUCCESS, invoke(second, "delete").first)
        assertEquals(WireValue.Integer(2), WireMap.decode(invoke(first, "retrieve").second)["state"])
    }

    @Test
    fun refusesCorruptCiphertextAndCanReplaceIt() {
        val module = AuthVaultModule(context)
        assertEquals(ModuleResultStatus.SUCCESS, invoke(module, "store", "first").first)
        val preferences = context.getSharedPreferences("dev.pam.auth.vault", Context.MODE_PRIVATE)
        val storageKey = Base64.encodeToString(key.toByteArray(), Base64.NO_WRAP or Base64.URL_SAFE)
        val bytes = Base64.decode(preferences.getString(storageKey, null), Base64.NO_WRAP)
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 1).toByte()
        preferences.edit().putString(storageKey, Base64.encodeToString(bytes, Base64.NO_WRAP)).commit()
        assertEquals(ModuleResultStatus.FAILURE, invoke(module, "retrieve").first)
        assertEquals(ModuleResultStatus.SUCCESS, invoke(module, "store", "replacement").first)
        assertEquals(WireValue.Text("replacement"), WireMap.decode(invoke(module, "retrieve").second)["secret"])
        invoke(module, "delete")
    }
}
