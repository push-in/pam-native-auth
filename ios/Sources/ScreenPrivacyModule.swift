import Foundation
import PamNative
import UIKit

public final class ScreenPrivacyModule: NativeModule, @unchecked Sendable {
    private var shield: PrivacyShield?

    public init() {}

    public func invoke(method: String, payload: Data, completion: @escaping ModuleCompletion) {
        DispatchQueue.main.async { [self] in
            let result: Result
            do {
                guard try WireMap.decode(payload).isEmpty else { throw PrivacyError.invalidPayload }
                switch method {
                case "conceal":
                    if shield == nil { shield = PrivacyShield() }
                    result = shield?.conceal() == true ? .concealed : .failed
                case "reveal":
                    result = shield?.reveal() == true ? .revealed : .failed
                default:
                    result = .failed
                }
                completion(.success, try WireMap.encode(["state": .integer(result.rawValue)]))
            } catch {
                if let response = try? WireMap.encode(["state": .integer(Result.failed.rawValue)]) {
                    completion(.success, response)
                } else { completion(.failure, Data()) }
            }
        }
    }

    private enum Result: Int64 { case concealed = 1, revealed, failed }
    private enum PrivacyError: Error { case invalidPayload }
}

/// Covers app scenes without claiming to prevent screenshots on iOS.
private final class PrivacyShield {
    private var covers: [ObjectIdentifier: UIWindow] = [:]
    private var observers: [NSObjectProtocol] = []

    init() {
        let center = NotificationCenter.default
        for event in [UIApplication.willResignActiveNotification, UIScene.willDeactivateNotification] {
            observers.append(center.addObserver(forName: event, object: nil, queue: .main) { [weak self] _ in
                _ = self?.conceal()
            })
        }
        observers.append(center.addObserver(forName: UIScene.didActivateNotification, object: nil, queue: .main) { [weak self] _ in
            self?.attachScenes()
        })
        observers.append(center.addObserver(forName: UIScene.didDisconnectNotification, object: nil, queue: .main) { [weak self] note in
            guard let scene = note.object as? UIWindowScene else { return }
            self?.covers.removeValue(forKey: ObjectIdentifier(scene))?.isHidden = true
        })
    }

    @discardableResult func conceal() -> Bool {
        attachScenes()
        for cover in covers.values { cover.isHidden = false }
        return !covers.isEmpty
    }

    func reveal() -> Bool {
        guard UIApplication.shared.applicationState == .active else { return false }
        attachScenes()
        let visible = covers.values.filter { $0.windowScene?.activationState == .foregroundActive }
        guard !visible.isEmpty else { return false }
        for cover in visible { cover.isHidden = true }
        return true
    }

    private func attachScenes() {
        for case let scene as UIWindowScene in UIApplication.shared.connectedScenes {
            let key = ObjectIdentifier(scene)
            guard covers[key] == nil else { continue }
            let cover = UIWindow(windowScene: scene)
            cover.windowLevel = UIWindow.Level(rawValue: UIWindow.Level.alert.rawValue + 1)
            let controller = UIViewController()
            controller.view.backgroundColor = .black
            controller.view.isAccessibilityElement = true
            controller.view.accessibilityLabel = "Protected content"
            cover.rootViewController = controller
            cover.isHidden = false
            covers[key] = cover
        }
    }

    deinit {
        for observer in observers { NotificationCenter.default.removeObserver(observer) }
        let windows = Array(covers.values)
        DispatchQueue.main.async { for window in windows { window.isHidden = true } }
    }
}
