package dev.pam.auth

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import dev.pam.nativeapp.modules.ModuleCompletion
import dev.pam.nativeapp.modules.ModuleResultStatus
import dev.pam.nativeapp.modules.NativeModule
import dev.pam.nativeapp.protocol.WireMap
import dev.pam.nativeapp.protocol.WireValue

class BiometricsModule(private val context: Context) : NativeModule, AutoCloseable {
    private val main = Handler(Looper.getMainLooper())
    private var closed = false
    private var cancelActive: (() -> Unit)? = null

    override fun invoke(method: String, payload: ByteArray, completion: ModuleCompletion) {
        main.post {
            if (closed) {
                if (method == "availability") complete(completion, "availability", Availability.UNAVAILABLE.code)
                else complete(completion, "state", Result.UNAVAILABLE.code)
                return@post
            }
            try {
                val values = WireMap.decode(payload)
                when (method) {
                    "availability" -> complete(completion, "availability", availability())
                    "authenticate" -> authenticate(values, completion)
                    else -> completion.complete(ModuleResultStatus.FAILURE, "Unknown biometric method".toByteArray())
                }
            } catch (_: Exception) {
                complete(completion, "state", Result.FAILED.code)
            }
        }
    }

    private fun availability(): Long = when (
        BiometricManager.from(context).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
    ) {
        BiometricManager.BIOMETRIC_SUCCESS -> Availability.AVAILABLE.code
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> Availability.NOT_ENROLLED.code
        else -> Availability.UNAVAILABLE.code
    }

    private fun authenticate(values: Map<String, WireValue>, completion: ModuleCompletion) {
        if (cancelActive != null) { complete(completion, "state", Result.BUSY.code); return }
        val activity = context as? FragmentActivity
        if (activity == null || !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            complete(completion, "state", Result.UNAVAILABLE.code); return
        }
        val reason = (values["reason"] as? WireValue.Text)?.value.orEmpty()
        val cancelLabel = (values["cancelLabel"] as? WireValue.Text)?.value.orEmpty()
        require(reason.isNotBlank() && reason.toByteArray().size <= 256)
        require(cancelLabel.isNotBlank() && cancelLabel.toByteArray().size <= 256)
        var finished = false
        var observer: DefaultLifecycleObserver? = null
        fun finish(result: Result) {
            if (finished) return
            finished = true
            observer?.let { activity.lifecycle.removeObserver(it) }
            cancelActive = null
            complete(completion, "state", result.code)
        }
        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = finish(Result.AUTHENTICATED)
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                finish(when (errorCode) {
                    BiometricPrompt.ERROR_CANCELED, BiometricPrompt.ERROR_USER_CANCELED, BiometricPrompt.ERROR_NEGATIVE_BUTTON -> Result.CANCELLED
                    BiometricPrompt.ERROR_LOCKOUT, BiometricPrompt.ERROR_LOCKOUT_PERMANENT -> Result.LOCKED_OUT
                    BiometricPrompt.ERROR_NO_BIOMETRICS, BiometricPrompt.ERROR_HW_NOT_PRESENT, BiometricPrompt.ERROR_HW_UNAVAILABLE -> Result.UNAVAILABLE
                    else -> Result.FAILED
                })
            }
        })
        val cancel = { finish(Result.CANCELLED); prompt.cancelAuthentication() }
        observer = object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) = cancel()
        }
        activity.lifecycle.addObserver(observer)
        cancelActive = cancel
        try {
            prompt.authenticate(BiometricPrompt.PromptInfo.Builder()
                .setTitle(reason)
                .setNegativeButtonText(cancelLabel)
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .setConfirmationRequired(true)
                .build())
        } catch (_: Exception) { finish(Result.FAILED) }
    }

    override fun close() { main.post { closed = true; cancelActive?.invoke() } }

    private fun complete(completion: ModuleCompletion, key: String, value: Long) {
        completion.complete(ModuleResultStatus.SUCCESS, WireMap.encode(mapOf(key to WireValue.Integer(value))))
    }

    private enum class Availability(val code: Long) { AVAILABLE(1), NOT_ENROLLED(2), UNAVAILABLE(3) }
    private enum class Result(val code: Long) { AUTHENTICATED(1), CANCELLED(2), UNAVAILABLE(3), LOCKED_OUT(4), FAILED(5), BUSY(6) }
}
