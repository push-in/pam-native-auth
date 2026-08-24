<!-- pam:product-page:start -->
<div align="center">

# PAM Native Auth

**Credentials belong in the device vault, not in application storage.**

Build secure sessions and OAuth 2.1/PKCE flows with Android Keystore and Apple Keychain primitives exposed through strict PHP APIs.

[![Latest version](https://img.shields.io/packagist/v/pushinbr/pam-native-auth?style=flat-square&label=stable)](https://packagist.org/packages/pushinbr/pam-native-auth)
[![CI](https://img.shields.io/github/actions/workflow/status/push-in/pam-native-auth/ci.yml?branch=main&style=flat-square&label=CI)](https://github.com/push-in/pam-native-auth/actions)
![PHP](https://img.shields.io/badge/PHP-8.5-777BB4?style=flat-square&logo=php&logoColor=white)
![Android](https://img.shields.io/badge/Android-API%2026%2B-3DDC84?style=flat-square&logo=android&logoColor=white)
![iOS](https://img.shields.io/badge/iOS-15%2B-000000?style=flat-square&logo=apple&logoColor=white)

**[Documentation](https://push-in.github.io/pam-docs/native/overview/) · [Quick start](#quick-start) · [What you can build](#what-you-can-build) · [PAM ecosystem](https://push-in.github.io/pam-docs/ecosystem/) · [Issues](https://github.com/push-in/pam-native-auth/issues)**

</div>

---

## Why PAM Native Auth

Build secure sessions and OAuth 2.1/PKCE flows with Android Keystore and Apple Keychain primitives exposed through strict PHP APIs. The public API is strictly typed for PHP 8.5; expensive or frame-sensitive work stays in Rust or the platform SDK instead of crossing the application boundary every frame.

| | |
| --- | --- |
| **Best for** | A focused capability you can add to any PAM Native application |
| **Native path** | Android Keystore · Apple Keychain |
| **Application model** | Composer package + generated native integration |
| **Design rule** | Independent module; no feed, vertical, or application template bundled |

## What you can build

- Encrypted refresh-token and session storage
- OAuth 2.1 authorization-code flows with PKCE
- Logout, revocation, and credential-rotation workflows

## Quick start

Already have a PAM Native project? Add only this capability:

```bash
pam composer require pushinbr/pam-native-auth
pam doctor --fix
```

New to PAM? Follow the **[five-minute PAM Native setup](https://push-in.github.io/pam-docs/native/overview/)** once, then return here. Your application stays a normal Composer project with a committed lockfile.
<!-- pam:product-page:end -->

## See it in action

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
- PAM Native `0.8.x` plugin protocol 1.

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

This package targets PAM Native `0.8.x`, Android API 26+, and iOS 15+ unless a platform-specific section above states a stricter requirement. Platform SDKs, credentials, entitlements, physical hardware, and store configuration remain application responsibilities.

- [PAM documentation](https://push-in.github.io/pam-docs/introduction/)
- [PAM Native overview](https://push-in.github.io/pam-docs/native/overview/)
- [Plugin and native capability model](https://push-in.github.io/pam-docs/native/plugins/)
- [Report an issue](https://github.com/push-in/pam-native-auth/issues)

Security vulnerabilities should be reported through the repository security policy or GitHub private vulnerability reporting, not a public issue.
