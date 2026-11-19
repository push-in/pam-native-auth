package dev.pam.auth

import android.content.Context
import android.os.Build
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import dev.pam.nativeapp.modules.ModuleCompletion
import dev.pam.nativeapp.modules.ModuleResultStatus
import dev.pam.nativeapp.modules.NativeModule
import dev.pam.nativeapp.protocol.WireMap
import dev.pam.nativeapp.protocol.WireValue

class BiometricsModule(private val context: Context) : NativeModule, AutoCloseable {
    private var legacyStarted = false
    private val compatibility by lazy {
        legacyStarted = true
        PlatformBiometricsCompatibility(context)
    }
    private val main = Handler(Looper.getMainLooper())
    private var closed = false
    private var cancelActive: (() -> Unit)? = null

    init {
        Reloads.track(this)
    }

    /** A hot reload is about to restart the PHP request (see [Reloads]). */
    private fun prepareReload() {
        main.post { cancelActive?.invoke() }
        // The legacy `biometrics` wire (LegacyBiometrics, Android 7.0+) too.
        if (legacyStarted) compatibility.cancelActive()
    }

    /**
     * A hot reload restarts the PHP request: the callback waiting for an open
     * prompt is gone with it, so the prompt answered nobody (pressing Cancel on
     * One UI did nothing). Every live module closes its prompt first. One
     * process-wide listener; modules are held weakly.
     */
    private object Reloads : dev.pam.nativeapp.HostEventListener {
        private val modules = java.util.Collections.synchronizedSet(
            java.util.Collections.newSetFromMap(java.util.WeakHashMap<BiometricsModule, Boolean>()),
        )

        init {
            dev.pam.nativeapp.HostEvents.register(this)
        }

        fun track(module: BiometricsModule) { modules += module }

        fun forget(module: BiometricsModule) { modules -= module }

        override fun prepareReload() {
            synchronized(modules) { modules.toList() }.forEach(BiometricsModule::prepareReload)
        }
    }

    override fun invoke(method: String, payload: ByteArray, completion: ModuleCompletion) {
        if (method == "legacy.status" || method == "legacy.authenticate") {
            compatibility.invoke(method.removePrefix("legacy."), payload, completion)
            return
        }
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
        // A new request replaces the visible prompt; the earlier request
        // completes Cancelled. A hot reload restarts the PHP request while the
        // prompt is open: the re-mounted screen asks again, and answering it
        // Busy left the old prompt's result with nobody to receive it (the
        // legacy module already replaced its prompt).
        cancelActive?.invoke()
        val activity = context as? FragmentActivity
        if (activity == null || !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            complete(completion, "state", Result.UNAVAILABLE.code); return
        }
        val reason = (values["reason"] as? WireValue.Text)?.value.orEmpty()
        val cancelLabel = (values["cancelLabel"] as? WireValue.Text)?.value.orEmpty()
        require(reason.isNotBlank() && reason.toByteArray().size <= 256)
        require(cancelLabel.isNotBlank() && cancelLabel.toByteArray().size <= 256)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            authenticatePlatform(activity, reason, cancelLabel, completion)
            return
        }
        var finished = false
        var observer: LifecycleEventObserver? = null
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
        observer = object : LifecycleEventObserver {
            override fun onStateChanged(owner: LifecycleOwner, event: Lifecycle.Event) {
                if (event == Lifecycle.Event.ON_STOP) cancel()
            }
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

    /**
     * Android 9+: the platform BiometricPrompt, as the legacy `biometrics`
     * module uses. With androidx.biometric's fragment-hosted prompt, pressing
     * the negative button on One UI 4 (Android 12, Galaxy S10) dismissed the
     * prompt without delivering onAuthenticationError, so PHP never received
     * the result. The platform prompt reports the negative button through its
     * own listener and every other outcome through the authentication callback.
     */
    @androidx.annotation.RequiresApi(Build.VERSION_CODES.P)
    private fun authenticatePlatform(
        activity: FragmentActivity,
        reason: String,
        cancelLabel: String,
        completion: ModuleCompletion,
    ) {
        val signal = CancellationSignal()
        var finished = false
        var observer: LifecycleEventObserver? = null
        fun finish(result: Result) {
            if (finished) return
            finished = true
            observer?.let { activity.lifecycle.removeObserver(it) }
            cancelActive = null
            complete(completion, "state", result.code)
        }
        val cancel = {
            finish(Result.CANCELLED)
            if (!signal.isCanceled) signal.cancel()
        }
        observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) cancel()
        }
        activity.lifecycle.addObserver(observer)
        cancelActive = cancel
        val executor = ContextCompat.getMainExecutor(activity)
        try {
            val builder = android.hardware.biometrics.BiometricPrompt.Builder(activity)
                .setTitle(reason)
                .setNegativeButton(cancelLabel, executor) { _, _ -> finish(Result.CANCELLED) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) builder.setConfirmationRequired(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                builder.setAllowedAuthenticators(android.hardware.biometrics.BiometricManager.Authenticators.BIOMETRIC_STRONG)
            }
            builder.build().authenticate(
                signal,
                executor,
                object : android.hardware.biometrics.BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: android.hardware.biometrics.BiometricPrompt.AuthenticationResult) =
                        finish(Result.AUTHENTICATED)

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) =
                        finish(platformResult(errorCode))
                },
            )
        } catch (_: Exception) {
            finish(Result.FAILED)
        }
    }

    override fun close() {
        Reloads.forget(this)
        main.post { closed = true; cancelActive?.invoke() }
    }

    private fun complete(completion: ModuleCompletion, key: String, value: Long) {
        completion.complete(ModuleResultStatus.SUCCESS, WireMap.encode(mapOf(key to WireValue.Integer(value))))
    }

    internal companion object {
        /** Maps a platform BiometricPrompt error to the PHP BiometricResult code. */
        fun platformResult(errorCode: Int): Result = when (errorCode) {
            android.hardware.biometrics.BiometricPrompt.BIOMETRIC_ERROR_CANCELED,
            android.hardware.biometrics.BiometricPrompt.BIOMETRIC_ERROR_USER_CANCELED -> Result.CANCELLED
            android.hardware.biometrics.BiometricPrompt.BIOMETRIC_ERROR_LOCKOUT,
            android.hardware.biometrics.BiometricPrompt.BIOMETRIC_ERROR_LOCKOUT_PERMANENT -> Result.LOCKED_OUT
            android.hardware.biometrics.BiometricPrompt.BIOMETRIC_ERROR_NO_BIOMETRICS,
            android.hardware.biometrics.BiometricPrompt.BIOMETRIC_ERROR_HW_NOT_PRESENT,
            android.hardware.biometrics.BiometricPrompt.BIOMETRIC_ERROR_HW_UNAVAILABLE -> Result.UNAVAILABLE
            else -> Result.FAILED
        }
    }

    private enum class Availability(val code: Long) { AVAILABLE(1), NOT_ENROLLED(2), UNAVAILABLE(3) }
    internal enum class Result(val code: Long) { AUTHENTICATED(1), CANCELLED(2), UNAVAILABLE(3), LOCKED_OUT(4), FAILED(5), BUSY(6) }
}
