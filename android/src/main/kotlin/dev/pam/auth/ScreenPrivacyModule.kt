package dev.pam.auth

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.fragment.app.FragmentActivity
import dev.pam.nativeapp.modules.ModuleCompletion
import dev.pam.nativeapp.modules.ModuleResultStatus
import dev.pam.nativeapp.modules.NativeModule
import dev.pam.nativeapp.protocol.WireMap
import dev.pam.nativeapp.protocol.WireValue

class ScreenPrivacyModule(private val context: Context) : NativeModule, AutoCloseable {
    private val main = Handler(Looper.getMainLooper())
    private var shield: PrivacyShield? = null
    private var closed = false

    override fun invoke(method: String, payload: ByteArray, completion: ModuleCompletion) {
        main.post {
            val result = try {
                require(WireMap.decode(payload).isEmpty())
                val activity = context as? FragmentActivity
                if (closed || activity == null || activity.isDestroyed || activity.isFinishing) Result.FAILED
                else when (method) {
                    "conceal" -> {
                        val current = shield ?: PrivacyShield(activity).also { shield = it }
                        current.conceal()
                        Result.CONCEALED
                    }
                    "reveal" -> if (shield?.reveal() == true) Result.REVEALED else Result.FAILED
                    else -> Result.FAILED
                }
            } catch (_: Exception) { Result.FAILED }
            completion.complete(ModuleResultStatus.SUCCESS, WireMap.encode(mapOf("state" to WireValue.Integer(result.code))))
        }
    }

    override fun close() {
        main.post {
            closed = true
            shield?.close()
            shield = null
        }
    }

    private enum class Result(val code: Long) { CONCEALED(1), REVEALED(2), FAILED(3) }
}
