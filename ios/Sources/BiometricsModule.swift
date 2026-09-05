import Foundation
import LocalAuthentication
import PamNative
import UIKit

public final class BiometricsModule: NativeModule, @unchecked Sendable {
    private var active: LAContext?
    private var cancellation: (() -> Void)?
    private var backgroundObserver: NSObjectProtocol?

    public init() {}

    public func invoke(method: String, payload: Data, completion: @escaping ModuleCompletion) {
        DispatchQueue.main.async { [self] in
            do {
                let values = try WireMap.decode(payload)
                switch method {
                case "availability":
                    let context = LAContext()
                    var error: NSError?
                    let available = context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &error)
                    let value: Int64 = available ? 1 : (error?.code == LAError.biometryNotEnrolled.rawValue ? 2 : 3)
                    complete("availability", value, completion)
                case "authenticate":
                    authenticate(values, completion)
                default:
                    completion(.failure, Data("Unknown biometric method".utf8))
                }
            } catch {
                complete("state", Result.failed.rawValue, completion)
            }
        }
    }

    private func authenticate(_ values: [String: WireValue], _ completion: @escaping ModuleCompletion) {
        guard active == nil else { complete("state", Result.busy.rawValue, completion); return }
        guard UIApplication.shared.applicationState == .active else {
            complete("state", Result.unavailable.rawValue, completion); return
        }
        guard case let .text(reason)? = values["reason"],
              case let .text(cancelLabel)? = values["cancelLabel"],
              !reason.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
              !cancelLabel.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
              reason.utf8.count <= 256, cancelLabel.utf8.count <= 256 else {
            complete("state", Result.failed.rawValue, completion); return
        }
        let context = LAContext()
        context.localizedCancelTitle = cancelLabel
        context.localizedFallbackTitle = ""
        context.touchIDAuthenticationAllowableReuseDuration = 0
        var error: NSError?
        guard context.canEvaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, error: &error) else {
            let result: Result = error?.code == LAError.biometryLockout.rawValue ? .lockedOut : .unavailable
            complete("state", result.rawValue, completion); return
        }
        active = context
        let finish: (Result) -> Void = { [weak self, weak context] result in
            guard let self, let context, self.active === context else { return }
            self.active = nil
            self.cancellation = nil
            if let observer = self.backgroundObserver {
                NotificationCenter.default.removeObserver(observer)
                self.backgroundObserver = nil
            }
            self.complete("state", result.rawValue, completion)
        }
        cancellation = { finish(.cancelled); context.invalidate() }
        backgroundObserver = NotificationCenter.default.addObserver(
            forName: UIApplication.didEnterBackgroundNotification, object: nil, queue: .main
        ) { [weak self] _ in self?.cancellation?() }
        context.evaluatePolicy(.deviceOwnerAuthenticationWithBiometrics, localizedReason: reason) { success, error in
            DispatchQueue.main.async {
                if success { finish(.authenticated); return }
                let code = (error as? LAError)?.code
                let result: Result
                switch code {
                case .userCancel, .appCancel, .systemCancel, .userFallback: result = .cancelled
                case .biometryLockout: result = .lockedOut
                case .biometryNotAvailable, .biometryNotEnrolled: result = .unavailable
                default: result = .failed
                }
                finish(result)
            }
        }
    }

    private func complete(_ key: String, _ value: Int64, _ completion: ModuleCompletion) {
        do { completion(.success, try WireMap.encode([key: .integer(value)])) }
        catch { completion(.failure, Data("Biometric response encoding failed".utf8)) }
    }

    deinit {
        active?.invalidate()
        if let observer = backgroundObserver { NotificationCenter.default.removeObserver(observer) }
    }

    private enum Result: Int64 {
        case authenticated = 1, cancelled, unavailable, lockedOut, failed, busy
    }
}
