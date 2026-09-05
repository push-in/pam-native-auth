import XCTest
import PamNative
@testable import PamAuth

final class BiometricsModuleTests: XCTestCase {
    private enum BiometricResult: Int64 {
        case authenticated = 1, cancelled, unavailable, lockedOut, failed, busy
    }

    func testMalformedPayloadFailsClosed() throws {
        let finished = expectation(description: "Malformed request completes")
        let module = BiometricsModule()
        module.invoke(method: "authenticate", payload: Data([0xff])) { status, payload in
            XCTAssertEqual(status, .success)
            do {
                let values = try WireMap.decode(payload)
                guard case let .integer(state)? = values["state"] else {
                    XCTFail("Expected an integer biometric result")
                    finished.fulfill()
                    return
                }
                XCTAssertEqual(state, BiometricResult.failed.rawValue, "Malformed input must produce Failed, never Authenticated")
            } catch {
                XCTFail("Invalid result: \(error)")
            }
            finished.fulfill()
        }
        wait(for: [finished], timeout: 5)
    }

    func testUnknownMethodRejectsRequest() throws {
        let finished = expectation(description: "Unknown method completes")
        let module = BiometricsModule()
        module.invoke(method: "unsupported", payload: try WireMap.encode([:])) { status, _ in
            XCTAssertEqual(status, .failure)
            finished.fulfill()
        }
        wait(for: [finished], timeout: 5)
    }
}
