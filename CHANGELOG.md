# Changelog

## Unreleased

## 0.3.2 - 2026-09-06

- Add `AuthVault::exists()` to check for a stored credential on Android and
  Apple platforms without returning or decrypting secret material.

## 0.3.1 - 2026-09-05

- Verify Android plugin lifecycle registration against the published PAM Native SDK.

## 0.3.0 - 2026-09-05

- Add system biometric authentication with sequential integer availability/result enums, cancellation, concurrency protection and lifecycle handling.
- Add native screen privacy with explicit reveal, Android screenshot/accessibility protection and iOS inactive-scene covers.
- Verify successful authentication, explicit cancellation and background privacy in the iOS simulator; verify Android system prompts and lifecycle behavior on API 36.
- Document that biometrics and vault access require an application-owned session gate.

## 0.2.1 - 2026-09-05

- Support PAM Native 1.x on PHP 8.5.
- Verify Android vault persistence across module instances, ciphertext integrity, replacement, and deletion on API 36.
## 0.1.0 - 2026-08-01

- Initial public release of the documented PAM Native package contract.
- Add bounded input validation, sequential integer protocol enums, automated
  package tests, and PHP 8.4/8.5 continuous integration.
