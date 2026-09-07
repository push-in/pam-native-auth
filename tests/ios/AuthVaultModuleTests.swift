import XCTest
import PamNative
@testable import PamAuth

final class AuthVaultModuleTests: XCTestCase {
    func testExistsReportsPresenceWithoutReturningSecret() throws {
        let key = "test.exists.\(UUID().uuidString)"
        var present = false
        var queriedKeys: [String] = []
        let module = AuthVaultModule(itemExists: { queriedKey in
            queriedKeys.append(queriedKey)
            return present
        })
        let missing = try invoke(module, "exists", ["key": .text(key)])
        XCTAssertEqual(missing["state"], .integer(2))
        XCTAssertNil(missing["secret"])

        present = true
        let found = try invoke(module, "exists", ["key": .text(key)])
        XCTAssertEqual(found["state"], .integer(1))
        XCTAssertNil(found["secret"])
        XCTAssertEqual(queriedKeys, [key, key])
    }

    private func invoke(_ module: AuthVaultModule, _ method: String, _ values: [String: WireValue]) throws -> [String: WireValue] {
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
