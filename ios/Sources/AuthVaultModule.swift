import Foundation
import PamNative
import Security

public final class AuthVaultModule: NativeModule, @unchecked Sendable {
    private let service = "dev.pam.auth.vault"

    public init() {}

    public func invoke(method: String, payload: Data, completion: @escaping ModuleCompletion) {
        do {
            let values = try WireMap.decode(payload)
            guard case let .text(key)? = values["key"] else { throw VaultError.invalidKey }
            switch method {
            case "store":
                guard case let .text(secret)? = values["secret"], case let .integer(accessibility)? = values["accessibility"] else {
                    throw VaultError.invalidSecret
                }
                try store(key: key, secret: secret, accessibility: accessibility)
                complete(["state": .integer(1)], completion)
            case "retrieve":
                if let secret = try retrieve(key: key) {
                    complete(["state": .integer(1), "secret": .text(secret)], completion)
                } else {
                    complete(["state": .integer(2)], completion)
                }
            case "delete":
                complete(["state": .integer(try delete(key: key) ? 1 : 2)], completion)
            default:
                throw VaultError.unknownMethod
            }
        } catch {
            completion(.failure, Data(String(describing: error).utf8))
        }
    }

    private func store(key: String, secret: String, accessibility: Int64) throws {
        let query = baseQuery(key: key)
        SecItemDelete(query as CFDictionary)
        var insert = query
        insert[kSecValueData as String] = Data(secret.utf8)
        insert[kSecAttrAccessible as String] = accessible(accessibility)
        let status = SecItemAdd(insert as CFDictionary, nil)
        guard status == errSecSuccess else { throw VaultError.security(status) }
    }

    private func retrieve(key: String) throws -> String? {
        var query = baseQuery(key: key)
        query[kSecReturnData as String] = true
        query[kSecMatchLimit as String] = kSecMatchLimitOne
        var result: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        if status == errSecItemNotFound { return nil }
        guard status == errSecSuccess, let data = result as? Data, let secret = String(data: data, encoding: .utf8) else {
            throw VaultError.security(status)
        }
        return secret
    }

    private func delete(key: String) throws -> Bool {
        let status = SecItemDelete(baseQuery(key: key) as CFDictionary)
        if status == errSecItemNotFound { return false }
        guard status == errSecSuccess else { throw VaultError.security(status) }
        return true
    }

    private func baseQuery(key: String) -> [String: Any] {
        [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: service, kSecAttrAccount as String: key]
    }

    private func accessible(_ value: Int64) -> CFString {
        switch value {
        case 1: kSecAttrAccessibleAfterFirstUnlock
        case 2: kSecAttrAccessibleWhenUnlocked
        default: kSecAttrAccessibleWhenUnlockedThisDeviceOnly
        }
    }

    private func complete(_ values: [String: WireValue], _ completion: ModuleCompletion) {
        do { completion(.success, try WireMap.encode(values)) }
        catch { completion(.failure, Data(String(describing: error).utf8)) }
    }
}

private enum VaultError: Error {
    case invalidKey
    case invalidSecret
    case unknownMethod
    case security(OSStatus)
}
