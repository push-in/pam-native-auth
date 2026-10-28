import Foundation
import LocalAuthentication
import PamNative

/// Face ID / Touch ID through LocalAuthentication (`System\Biometrics`).
/// BiometricKind: 1 none, 2 fingerprint, 3 face, 4 iris, 5 other.
/// BiometricError: 1 none, 2 cancelled, 3 unavailable, 4 lockout.
final class PlatformBiometricsCompatibility: NativeModule {
    init() {}
    func invoke(method: String, payload: Data, completion: @escaping ModuleCompletion) {
        switch method {
        case "status":
            DispatchQueue.main.async {
                completion(.success, (try? WireMap.encode(["available": .flag(Self.kind() != 1), "kind": .integer(Self.kind())])) ?? Data())
            }
        case "authenticate":
            guard let values = try? WireMap.decode(payload),
                  case let .text(title)? = values["title"], !title.isEmpty,
                  case let .text(cancel)? = values["cancelLabel"], !cancel.isEmpty else {
                completion(.failure, Data("Biometric prompt title and cancel label are required".utf8))
                return
            }
            let context = LAContext()
            context.localizedCancelTitle = cancel
            context.localizedFallbackTitle = ""
            var error: NSError?
            guard context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &error) else {
                completion(.success, Self.result(false, error: Self.code(error)))
                return
            }
            context.evaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, localizedReason: title) { passed, failure in
                completion(.success, Self.result(passed, error: passed ? 1 : Self.code(failure as NSError?)))
            }
        default:
            completion(.failure, Data("Unknown biometrics method \(method)".utf8))
        }
    }

    private static func kind() -> Int64 {
        let context = LAContext()
        guard context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: nil) else { return 1 }
        switch context.biometryType {
        case .touchID: return 2
        case .faceID: return 3
        case .none: return 1
        default: return 5
        }
    }

    private static func code(_ error: NSError?) -> Int64 {
        guard let error, error.domain == LAError.errorDomain else { return 3 }
        switch LAError.Code(rawValue: error.code) {
        case .userCancel?, .appCancel?, .systemCancel?, .userFallback?: return 2
        case .biometryLockout?: return 4
        default: return 3
        }
    }

    private static func result(_ passed: Bool, error: Int64) -> Data {
        (try? WireMap.encode(["authenticated": .flag(passed), "error": .integer(error)])) ?? Data()
    }
}

