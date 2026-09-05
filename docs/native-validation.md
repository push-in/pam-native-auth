# Native authentication and privacy validation

## Verified on 2026-09-05

The iOS GitHub Actions run [33940571482](https://github.com/push-in/pam-native-auth/actions/runs/33940571482), commit `9ff193d`, completed:

- Three native XCTest cases: malformed biometric payload, unknown biometric method, and privacy reveal before conceal all reject access.
- Two host-app UI tests: background/foreground retains the privacy cover and blocks interaction with reference content; the system biometric prompt returns Authenticated after a simulated biometric match.
- Host build and launch against published PAM Native SDK 1.0.16. The workflow preserves a launch screenshot and both xcresult bundles as artifacts.

The UI host deliberately reveals its test content after activation unless the keep-covered test flag is supplied. This is test scaffolding, not a production session gate. Applications must authorize reveal themselves.

Android API 36 local instrumentation previously exercised a system fingerprint match and four privacy cases: missing activity, explicit-reveal lifecycle, recents pixel inspection, and accessibility conceal/restore. FLAG_SECURE remains enabled on the protected Activity; screenshots are therefore blocked on Android. iOS screenshot prevention is not provided.

## Remaining coverage

- iOS lockout interaction and physical-device non-match behavior.
- Older supported Android API levels and physical-device coverage.
- Production app integration: vault/session gating, expiration/revocation, navigation on biometric failure, and foreground/background races.

Passing isolated library tests does not establish that an application's session gate is implemented or secure.

Android API 36 additional instrumentation on 2026-09-05: `backgroundingCancelsPromptOnce` passed (one test, zero skipped). A pending enrolled-biometric request is cancelled when its Activity stops; returning to RESUMED does not deliver a second callback. Tested in the isolated plugin host; Linkinpay dev app was reopened afterward.

Android API 36: `closedModuleCannotOpenAnotherPrompt` passed in the isolated host (one test, zero failures/skips). Calling close twice and then authentication/availability returns Unavailable; the disposed module cannot create a new prompt. Linkinpay was brought back to the foreground after instrumentation.

Regression after the Android disposal fix: all three lifecycle instrumentation cases passed together on API 36 (3 tests, 0 failures/errors/skips): closed-instance rejection, concurrent-request rejection with exactly-once cancellation, and cancellation on backgrounding. Report captured locally in `/tmp/pam-biometric-lifecycle.log`.

## iOS cancellation verified

Run [33942786380](https://github.com/push-in/pam-native-auth/actions/runs/33942786380), commit `e28cc1f`, passed all three native tests and all three host UI tests. Simulated biometric non-matches expose the SpringBoard “Face Not Recognized” alert; explicitly tapping its Cancel button produces Cancelled (state 2), never Authenticated. Background privacy and simulated successful authentication also passed in that run. This is simulator evidence, not physical-device or lockout certification.
