import XCTest

final class PrivacyUITests: XCTestCase {
    func testBackgroundReturnKeepsReferenceContentCovered() {
        let app = XCUIApplication()
        app.launchArguments = ["--keep-covered-on-return"]
        app.launch()
        let reference = app.staticTexts["result"]
        XCTAssertTrue(reference.waitForExistence(timeout: 10))
        XCTAssertTrue(reference.isHittable)
        XCUIDevice.shared.press(.home)
        app.activate()
        XCTAssertTrue(app.staticTexts["Protected content"].waitForExistence(timeout: 10))
        XCTAssertFalse(reference.isHittable)
    }
}
