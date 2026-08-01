# PAM Native Auth

Production authentication foundations for PAM Native applications. Secrets are encrypted by Android Keystore or stored by Apple Keychain instead of being written to PHP files, preferences, or application databases.

```bash
composer require pushinbr/pam-native-auth
pam mobile codegen
pam mobile ios:prepare
```

```php
use Pam\Native\Auth\AuthVault;
use Pam\Native\Auth\Pkce;

$pkce = Pkce::generate(); // Send $pkce->challenge to your OAuth 2.1 authorization server.

(new AuthVault())->store('session.refresh-token', $refreshToken, function ($state, $error): void {
    // AuthOperationState is an integer-backed enum.
});
```

The vault uses AES-256-GCM with a non-exportable Android Keystore key. On Apple platforms it uses a generic-password Keychain item and defaults to `WhenUnlockedThisDeviceOnly`. PKCE uses SHA-256 (`S256`) and a cryptographically random verifier.

Do not store user passwords. Store short-lived sessions or refresh tokens, rotate them server-side, and delete the local credential on logout or revocation.

## Platform support

- Android 8.0+ (API 26): Android Keystore + AES-GCM.
- iOS 15+: Security.framework Keychain.
- PAM Native `0.6.x` plugin protocol 1.

Passkeys and interactive OAuth authorization are deliberately separate from the vault because they require an app presentation context, associated domains, and server-side challenge verification. Those flows will be added only with end-to-end app lifecycle support.
