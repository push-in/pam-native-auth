# PAM Native Auth

## Start here

This is a Composer extension for PAM Native. Install the PAM Runtime, create a native project, and then add this package through PAM’s verified Composer toolchain:

```bash
curl --proto '=https' --proto-redir '=https' --tlsv1.2 \
    --connect-timeout 15 --max-time 60 --max-filesize 1048576 -fsSL \
    https://github.com/push-in/pam/releases/latest/download/install.sh | sh

pam init my-app --template native
cd my-app
pam composer require pushinbr/pam-native-auth
pam doctor --fix
```


Production authentication foundations for PAM Native applications. Secrets are encrypted by Android Keystore or stored by Apple Keychain instead of being written to PHP files, preferences, or application databases.

```bash
pam add auth
pam doctor
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


## What installation does

`pam add auth` resolves the official compatible package, performs a non-mutating Composer preflight, updates the normal `composer.json` and `composer.lock`, refreshes generated native integration when required, and leaves the project ready for `pam doctor` validation.

Use `pam packages` to inspect availability and `pam remove auth` to uninstall the capability safely. Direct Composer commands are an advanced interoperability path; PAM is the supported application workflow.

## API guide

| API | Responsibility |
| --- | --- |
| `AuthVault` | Store, retrieve, and delete encrypted secrets. |
| `Pkce` / `PkcePair` | Generate and verify OAuth 2.1 S256 PKCE material. |
| `CredentialAccessibility` | Choose the native credential accessibility policy. |
| `AuthOperationState` | Typed result state for vault operations. |

All coded states, kinds, and variants are sequential integer-backed enums. Use enum cases in application code; do not depend on raw wire numbers.

## Production checklist

- Keep access tokens short-lived and rotate refresh tokens server-side.
- Delete credentials on logout, revocation, or account removal.
- Never log secrets, verifiers, authorization codes, or token responses.
- Run `pam doctor`, `pam test`, and a signed release build on every supported platform.
- Exercise denial, cancellation, backgrounding, process restart, and offline behavior before release.

## Troubleshooting

- **Vault callbacks fail:** confirm the app is active and inspect the callback error.
- **OAuth challenge mismatch:** preserve the exact verifier until the authorization callback.
- **Credentials disappear after reinstall:** device-only storage is intentionally app-scoped.
- **Native integration is stale:** run `pam doctor --fix`, rebuild the native host, and inspect the first reported diagnostic.

## Compatibility and support

This package targets PAM Native `0.6.x`, Android API 26+, and iOS 15+ unless a platform-specific section above states a stricter requirement. Platform SDKs, credentials, entitlements, physical hardware, and store configuration remain application responsibilities.

- [PAM documentation](https://push-in.github.io/pam-docs/introduction/)
- [PAM Native overview](https://push-in.github.io/pam-docs/native/overview/)
- [Plugin and native capability model](https://push-in.github.io/pam-docs/native/plugins/)
- [Report an issue](https://github.com/push-in/pam-native-auth/issues)

Security vulnerabilities should be reported through the repository security policy or GitHub private vulnerability reporting, not a public issue.
