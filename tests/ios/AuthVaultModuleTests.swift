import XCTest
import PamNative
@testable import PamAuth

final class AuthVaultModuleTests: XCTestCase {
    private let module = AuthVaultModule()

    func testExistsReportsPresenceWithoutReturningSecret() throws {
        let key = "test.exists.\(UUID().uuidString)"
        _ = try invoke("delete", ["key": .text(key)])
        let missing = try invoke("exists", ["key": .text(key)])
        XCTAssertEqual(missing["state"], .integer(2))
        XCTAssertNil(missing["secret"])

        let stored = try invoke("store", [
            "key": .text(key),
            "secret": .text("test-only-secret"),
            "accessibility": .integer(3),
        ])
        XCTAssertEqual(stored["state"], .integer(1))
        let present = try invoke("exists", ["key": .text(key)])
        XCTAssertEqual(present["state"], .integer(1))
        XCTAssertNil(present["secret"])

        _ = try invoke("delete", ["key": .text(key)])
        XCTAssertEqual(try invoke("exists", ["key": .text(key)])["state"], .integer(2))
    }

    private func invoke(_ method: String, _ values: [String: WireValue]) throws -> [String: WireValue] {
        let completed = expectation(description: method)
        var response: Result<[String: WireValue], Error>?
        module.invoke(method: method, payload: try WireMap.encode(values)) { status, payload in
            do {
                guard status == .success else {
                    throw VaultTestError.failed(String(data: payload, encoding: .utf8) ?? "unknown native error")
                }
                response = .success(try WireMap.decode(payload))
            } catch {
                response = .failure(error)
            }
            completed.fulfill()
        }
        wait(for: [completed], timeout: 5)
        return try XCTUnwrap(response).get()
    }
}

private enum VaultTestError: Error { case failed(String) }
