package dev.pam.auth

import android.content.Context
import dev.pam.nativeapp.modules.ModuleCompletion
import dev.pam.nativeapp.modules.NativeModule

/** One-version wire adapter for the former core biometrics module. */
class LegacyBiometricsModule(context: Context) : NativeModule, AutoCloseable {
    private val auth = BiometricsModule(context)
    override fun invoke(method: String, payload: ByteArray, completion: ModuleCompletion) =
        auth.invoke("legacy.$method", payload, completion)
    override fun close() = auth.close()
}
