# Screen privacy — implementation requirements

Status: implementation under certification, not published. The installed PAM
Native 1.0.17 and published auth library do not provide this protection.

The Linkinpay app needs native protection before displaying a restored session.
PHP lifecycle rendering alone is insufficient evidence that an operating-system
snapshot cannot contain account information.

## Required behavior

- Conceal account content in the application switcher when backgrounded.
- Keep the concealment in place on return until the app's session gate permits
  display; do not briefly expose a restored financial route before unlocking.
- Handle activity recreation, scene reconnect, cancellation, and module disposal.
- Android protection also prevents window screenshots via FLAG_SECURE. This is
  part of enabling the privacy gate and remains set for the Activity lifetime,
  including after module disposal. iOS covers scenes but does not prevent screenshots.
  Applications must generate shareable receipts separately from window captures.
- Never use private iOS APIs or a secure-text-field rendering workaround.
- Report unsupported or failed native operations to PHP; no success fallback.
- Avoid receiving or storing biometrics; use the biometric library's system prompt.

## Integration and release gate

Implement native behavior in this library or the upstream PAM Native repository,
not in Linkinpay's generated Android/iOS folders or its vendor installation.
Expose a documented PHP facade and test its bridge contract. Certify real native
background/foreground snapshots, cancellation, and recreation on Android and iOS.
Publish only after these checks, then install the published package in Linkinpay.

A biometric prompt succeeding does not itself enforce vault access or protect
snapshots. App navigation and credential use must remain gated independently.

## Android certification finding

An onPause cover and setRecentsScreenshotEnabled(false) alone still exposed the
reference surface in the API36 app switcher. FLAG_SECURE plus the native cover
passed the instrumented visual check: reference pixels were visible before
protection, absent in recents after enabling protection, and concealed on return.
Three instrumented tests passed on Pam_API_36. Older API levels remain untested.
