import XCTest

final class PrivacyUITests: XCTestCase {
    func testSystemBiometricsAuthenticates() {
        let app = XCUIApplication()
        addUIInterruptionMonitor(withDescription: "Face ID permission") { alert in
            for label in ["Allow", "OK"] {
                if alert.buttons[label].exists { alert.buttons[label].tap(); return true }
            }
            return false
        }
        app.launch()
        let authenticate = app.buttons["authenticate"]
        XCTAssertTrue(authenticate.waitForExistence(timeout: 10))
        authenticate.tap()
        app.tap()
        let result = app.staticTexts["result"]
        let authenticated = NSPredicate(format: "label == %@", "Biometric result: 1")
        expectation(for: authenticated, evaluatedWith: result)
        waitForExpectations(timeout: 25)
    }

    func testSystemBiometricsCancellationDoesNotAuthenticate() {
        let app = XCUIApplication()
        app.launch()
        let authenticate = app.buttons["authenticate"]
        XCTAssertTrue(authenticate.waitForExistence(timeout: 10))
        authenticate.tap()
        let cancel = app.buttons["Cancel"]
        let systemCancel = XCUIApplication(bundleIdentifier: "com.apple.springboard").buttons["Cancel"]
        if cancel.waitForExistence(timeout: 5) {
            cancel.tap()
        } else {
            XCTAssertTrue(systemCancel.waitForExistence(timeout: 5))
            systemCancel.tap()
        }
        let result = app.staticTexts["result"]
        expectation(for: NSPredicate(format: "label == %@", "Biometric result: 2"), evaluatedWith: result)
        waitForExpectations(timeout: 10)
    }

    func testBackgroundReturnKeepsReferenceContentCovered() {
        let app = XCUIApplication()
        app.launchArguments = ["--keep-covered-on-return"]
        app.launch()
        let reference = app.staticTexts["result"]
        XCTAssertTrue(reference.waitForExistence(timeout: 10))
        XCTAssertTrue(reference.isHittable)
        XCUIDevice.shared.press(.home)
        app.activate()
        XCTAssertTrue(app.descendants(matching: .any)["Protected content"].waitForExistence(timeout: 10))
        XCTAssertFalse(reference.isHittable)
    }
}
