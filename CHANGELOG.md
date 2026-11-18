# Changelog

## 0.4.5 - 2026-11-18

- Fixed: `Biometrics::authenticate()` called from `onMounted` under `pam dev`
  never completed on a Galaxy S10 (One UI 4, Android 12): the first hot
  reload restarts the PHP request right after the first mount, the open
  prompt kept answering the request that no longer existed, and pressing
  Cancel did nothing. The module now closes its prompt when PAM Native is
  about to reload (`HostEvents.prepareReload`), and a new request replaces
  an active prompt (the earlier request completes Cancelled) instead of
  answering Busy, on Android and iOS, as the legacy `biometrics` module
  already did. Reproduced and verified on the S10 (logcat: the prompt closes
  with error 5 before "Restarting PHP request for hot reload").
- Tests: instrumented tests for replacement and reload closing.

## 0.4.4 - 2026-11-18

- Fixed: LegacyBiometrics works on Android 7.0–9 through androidx.biometric (weak biometrics, fingerprint kind).

## 0.4.3 - 2026-11-17

- Android 7.0/7.1 support: the plugin declares minSdk 24 and every newer Android API it uses has a version check and fallback. Requires PAM Native 1.53.1 or later.

## 0.4.2 - 2026-11-17

- Fixed: `Biometrics::authenticate()` (`auth.biometrics`) on Android 9+ uses
  the platform BiometricPrompt, as the `biometrics` compatibility module does.
  With androidx.biometric's fragment-hosted prompt, pressing the cancel button
  on One UI 4 (Android 12, Galaxy S10) closed the prompt without reporting the
  error, so the PHP callback never ran and the screen stayed waiting. Success,
  cancel (button, back, app sent to background), lockout, unavailable and
  other errors all complete the callback; Android 8 keeps androidx.biometric.
- Tests: platform error mapping for every outcome (instrumented).

## 0.4.1

- Align AppCompat 1.7.1 with the host test configuration so biometric dependencies do not force an older test classpath.

## 0.4.0

- Owns the optional biometric compatibility capability extracted from the 1.46 core.
- Preserves PHP aliases and native contracts during migration.


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
