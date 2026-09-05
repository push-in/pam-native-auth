import UIKit
import PamNative
import PamAuth

@main
final class AppDelegate: UIResponder, UIApplicationDelegate {
    func application(_ application: UIApplication, configurationForConnecting session: UISceneSession, options: UIScene.ConnectionOptions) -> UISceneConfiguration {
        let configuration = UISceneConfiguration(name: "Certification", sessionRole: session.role)
        configuration.delegateClass = SceneDelegate.self
        return configuration
    }
}

final class SceneDelegate: UIResponder, UIWindowSceneDelegate {
    var window: UIWindow?
    private let privacy = ScreenPrivacyModule()
    private let biometrics = BiometricsModule()
    private let result = UILabel()
    private var firstActivation = true

    func scene(_ scene: UIScene, willConnectTo session: UISceneSession, options: UIScene.ConnectionOptions) {
        guard let scene = scene as? UIWindowScene else { return }
        let window = UIWindow(windowScene: scene)
        let controller = UIViewController()
        controller.view.backgroundColor = .systemRed
        let authenticate = UIButton(type: .system)
        authenticate.setTitle("Authenticate", for: .normal)
        authenticate.accessibilityIdentifier = "authenticate"
        authenticate.addTarget(self, action: #selector(authenticateDevice), for: .touchUpInside)
        result.text = "Protected reference content"
        result.accessibilityIdentifier = "result"
        let stack = UIStackView(arrangedSubviews: [result, authenticate])
        stack.axis = .vertical
        stack.spacing = 32
        stack.translatesAutoresizingMaskIntoConstraints = false
        controller.view.addSubview(stack)
        NSLayoutConstraint.activate([
            stack.centerXAnchor.constraint(equalTo: controller.view.centerXAnchor),
            stack.centerYAnchor.constraint(equalTo: controller.view.centerYAnchor)
        ])
        window.rootViewController = controller
        self.window = window
        window.makeKeyAndVisible()
    }

    func sceneDidBecomeActive(_ scene: UIScene) {
        if !firstActivation && ProcessInfo.processInfo.arguments.contains("--keep-covered-on-return") { return }
        firstActivation = false
        // Fixture only: explicitly reveal so UI tests can trigger the system prompt.
        // Production applications must authorize this via their session gate.
        privacy.invoke(method: "conceal", payload: try! WireMap.encode([:])) { [weak self] _, _ in
            self?.privacy.invoke(method: "reveal", payload: try! WireMap.encode([:])) { _, _ in }
        }
    }

    @objc private func authenticateDevice() {
        let payload = try! WireMap.encode(["reason": .text("Certify PAM biometrics"), "cancelLabel": .text("Cancel")])
        biometrics.invoke(method: "authenticate", payload: payload) { [weak self] _, data in
            guard let values = try? WireMap.decode(data), case let .integer(state)? = values["state"] else { return }
            self?.result.text = "Biometric result: \(state)"
        }
    }
}
