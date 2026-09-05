# Native authentication and privacy validation

## Verified on 2026-09-05

The iOS GitHub Actions run [33940571482](https://github.com/push-in/pam-native-auth/actions/runs/33940571482), commit `9ff193d`, completed:

- Three native XCTest cases: malformed biometric payload, unknown biometric method, and privacy reveal before conceal all reject access.
- Two host-app UI tests: background/foreground retains the privacy cover and blocks interaction with reference content; the system biometric prompt returns Authenticated after a simulated biometric match.
- Host build and launch against published PAM Native SDK 1.0.16. The workflow preserves a launch screenshot and both xcresult bundles as artifacts.

The UI host deliberately reveals its test content after activation unless the keep-covered test flag is supplied. This is test scaffolding, not a production session gate. Applications must authorize reveal themselves.

Android API 36 local instrumentation previously exercised a system fingerprint match and four privacy cases: missing activity, explicit-reveal lifecycle, recents pixel inspection, and accessibility conceal/restore. FLAG_SECURE remains enabled on the protected Activity; screenshots are therefore blocked on Android. iOS screenshot prevention is not provided.

## Remaining coverage

- iOS system-prompt cancellation, non-match and lockout interaction.
- Older supported Android API levels and physical-device coverage.
- Production app integration: vault/session gating, expiration/revocation, navigation on biometric failure, and foreground/background races.

Passing isolated library tests does not establish that an application's session gate is implemented or secure.
