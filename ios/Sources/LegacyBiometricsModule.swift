import Foundation
import PamNative

/// One-version adapter for the former core biometrics module.
public final class LegacyBiometricsModule: NativeModule {
    private let auth = BiometricsModule()
    public init() {}
    public func invoke(method: String, payload: Data, completion: @escaping ModuleCompletion) {
        auth.invoke(method: "legacy." + method, payload: payload, completion: completion)
    }
}
