import XCTest
import PamNative
@testable import PamAuth

final class ScreenPrivacyModuleTests: XCTestCase {
    private enum PrivacyResult: Int64 { case concealed = 1, revealed, failed }

    func testRevealWithoutProtectionFailsClosed() throws {
        let completed = expectation(description: "Privacy result")
        let module = ScreenPrivacyModule()
        module.invoke(method: "reveal", payload: try WireMap.encode([:])) { status, payload in
            XCTAssertEqual(status, .success)
            do {
                guard case let .integer(value)? = try WireMap.decode(payload)["state"] else {
                    XCTFail("Missing integer result")
                    completed.fulfill()
                    return
                }
                XCTAssertEqual(value, PrivacyResult.failed.rawValue)
            } catch { XCTFail("Invalid response") }
            completed.fulfill()
        }
        wait(for: [completed], timeout: 5)
    }
}
