package dev.pam.auth

import dev.pam.nativeapp.modules.*

import android.app.Activity
import android.content.pm.PackageManager
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import dev.pam.nativeapp.protocol.WireMap
import dev.pam.nativeapp.protocol.WireValue

/**
 * System biometric prompt (fingerprint / face / iris) through the platform
 * BiometricPrompt (Android 10+), and through androidx.biometric with a weak
 * biometric on Android 7.0-9 (fingerprint; the platform BiometricManager
 * starts at 10), as react-native-keychain's BIOMETRY_ANY did. `status`
 * reports availability and the hardware kind; `authenticate` shows the prompt
 * and answers whether the user passed it. Cancelling, the negative button or
 * a lockout answer false.
 */
internal class PlatformBiometricsCompatibility(context: android.content.Context) : NativeModule {
    private val activity = context as Activity

    /**
     * Closes the open prompt and answers its request cancelled (main thread).
     * A new request replaces the open prompt, and a hot reload closes it
     * (BiometricsModule forwards HostEvents.prepareReload): the PHP request
     * waiting for it is about to be gone.
     */
    private var cancelPending: (() -> Unit)? = null

    /** Closes the open prompt, if any; its request completes cancelled. */
    fun cancelActive() {
        activity.runOnUiThread { cancelPending?.invoke() }
    }

    override fun invoke(method: String, payload: ByteArray, completion: ModuleCompletion) {
        runCatching {
            when (method) {
                "status" -> completion.complete(ModuleResultStatus.SUCCESS, statusSnapshot())
                "authenticate" -> {
                    val values = WireMap.decode(payload)
                    val title = (values["title"] as? WireValue.Text)?.value?.takeIf(String::isNotBlank)
                        ?: error("Biometric prompt title is required")
                    val cancel = (values["cancelLabel"] as? WireValue.Text)?.value?.takeIf(String::isNotBlank)
                        ?: error("Biometric prompt cancel label is required")
                    val subtitle = (values["subtitle"] as? WireValue.Text)?.value?.takeIf(String::isNotBlank)
                    activity.runOnUiThread { authenticate(title, subtitle, cancel, completion) }
                }
                else -> error("Unknown biometrics method $method")
            }
        }.onFailure {
            completion.complete(ModuleResultStatus.FAILURE, (it.message ?: "Biometric operation failed").toByteArray())
        }
    }

    private fun authenticate(title: String, subtitle: String?, cancel: String, completion: ModuleCompletion) {
        if (!available()) {
            completion.complete(ModuleResultStatus.SUCCESS, result(false, ERROR_UNAVAILABLE))
            return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            authenticateCompat(title, subtitle, cancel, completion)
            return
        }
        cancelPending?.invoke()
        val signal = CancellationSignal()
        var settled = false
        lateinit var close: () -> Unit
        val settle = { passed: Boolean, error: Int ->
            if (!settled) {
                settled = true
                if (cancelPending === close) cancelPending = null
                completion.complete(ModuleResultStatus.SUCCESS, result(passed, error))
            }
        }
        close = {
            settle(false, ERROR_CANCELLED)
            signal.cancel()
        }
        cancelPending = close
        val executor = activity.mainExecutor
        val builder = BiometricPrompt.Builder(activity)
            .setTitle(title)
            .setNegativeButton(cancel, executor) { _, _ -> settle(false, ERROR_CANCELLED) }
        subtitle?.let(builder::setSubtitle)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
        }
        runCatching {
            builder.build().authenticate(
                signal,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) =
                        settle(true, ERROR_NONE)

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) =
                        settle(
                            false,
                            if (errorCode == BiometricPrompt.BIOMETRIC_ERROR_LOCKOUT ||
                                errorCode == BiometricPrompt.BIOMETRIC_ERROR_LOCKOUT_PERMANENT
                            ) {
                                ERROR_LOCKOUT
                            } else {
                                ERROR_CANCELLED
                            },
                        )
                },
            )
        }.onFailure { settle(false, ERROR_UNAVAILABLE) }
    }

    /** Android 7.0-9: androidx.biometric (FingerprintManager below 9, the platform prompt on 9). */
    private fun authenticateCompat(title: String, subtitle: String?, cancel: String, completion: ModuleCompletion) {
        val host = activity as? FragmentActivity
        if (host == null || host.isFinishing || host.supportFragmentManager.isStateSaved) {
            completion.complete(ModuleResultStatus.SUCCESS, result(false, ERROR_UNAVAILABLE))
            return
        }
        // The replaced request answers now: androidx.biometric shares one
        // prompt fragment per activity, so the old callback is not guaranteed
        // to run once a new prompt takes it over.
        cancelPending?.invoke()
        var settled = false
        lateinit var prompt: androidx.biometric.BiometricPrompt
        lateinit var close: () -> Unit
        val settle = { passed: Boolean, error: Int ->
            if (!settled) {
                settled = true
                if (cancelPending === close) cancelPending = null
                completion.complete(ModuleResultStatus.SUCCESS, result(passed, error))
            }
        }
        prompt = androidx.biometric.BiometricPrompt(
            host,
            ContextCompat.getMainExecutor(host),
            object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: androidx.biometric.BiometricPrompt.AuthenticationResult) =
                    settle(true, ERROR_NONE)

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) =
                    settle(false, compatError(errorCode))
            },
        )
        close = {
            settle(false, ERROR_CANCELLED)
            prompt.cancelAuthentication()
        }
        cancelPending = close
        val info = androidx.biometric.BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setNegativeButtonText(cancel)
            .setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK)
        subtitle?.let(info::setSubtitle)
        runCatching { prompt.authenticate(info.build()) }.onFailure { settle(false, ERROR_UNAVAILABLE) }
    }

    private fun available(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return androidx.biometric.BiometricManager.from(activity)
                .canAuthenticate(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
                androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
        }
        val manager = activity.getSystemService(BiometricManager::class.java) ?: return false
        val status = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
        } else {
            @Suppress("DEPRECATION")
            manager.canAuthenticate()
        }
        return status == BiometricManager.BIOMETRIC_SUCCESS
    }

    /** BiometricKind: 1 none, 2 fingerprint, 3 face, 4 iris, 5 other. */
    private fun kind(): Int {
        if (!available()) return KIND_NONE
        val features = activity.packageManager
        return when {
            features.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT) -> KIND_FINGERPRINT
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                features.hasSystemFeature(PackageManager.FEATURE_FACE) -> KIND_FACE
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                features.hasSystemFeature(PackageManager.FEATURE_IRIS) -> KIND_IRIS
            else -> KIND_OTHER
        }
    }

    private fun statusSnapshot(): ByteArray {
        val kind = kind()
        return WireMap.encode(
            mapOf(
                "available" to WireValue.Flag(kind != KIND_NONE),
                "kind" to WireValue.Integer(kind.toLong()),
            ),
        )
    }

    private fun result(passed: Boolean, error: Int) = WireMap.encode(
        mapOf(
            "authenticated" to WireValue.Flag(passed),
            "error" to WireValue.Integer(error.toLong()),
        ),
    )

    internal companion object {
        /** Maps an androidx.biometric error to BiometricError (lockout, unavailable, otherwise cancelled). */
        fun compatError(errorCode: Int): Int = when (errorCode) {
            androidx.biometric.BiometricPrompt.ERROR_LOCKOUT,
            androidx.biometric.BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> ERROR_LOCKOUT
            androidx.biometric.BiometricPrompt.ERROR_HW_NOT_PRESENT,
            androidx.biometric.BiometricPrompt.ERROR_HW_UNAVAILABLE,
            androidx.biometric.BiometricPrompt.ERROR_NO_BIOMETRICS -> ERROR_UNAVAILABLE
            else -> ERROR_CANCELLED
        }

        const val KIND_NONE = 1
        const val KIND_FINGERPRINT = 2
        const val KIND_FACE = 3
        const val KIND_IRIS = 4
        const val KIND_OTHER = 5

        /** BiometricError: 1 none, 2 cancelled, 3 unavailable, 4 lockout. */
        const val ERROR_NONE = 1
        const val ERROR_CANCELLED = 2
        const val ERROR_UNAVAILABLE = 3
        const val ERROR_LOCKOUT = 4
    }
}
