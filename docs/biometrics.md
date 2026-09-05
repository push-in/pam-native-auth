# Device biometrics

`Biometrics::availability()` returns `BiometricAvailability` and
`Biometrics::authenticate($reason, $complete, $cancelLabel)` returns a
`BiometricResult`. Both callbacks run through the normal PAM native bridge.

Android requires a strong enrolled biometric and a resumed FragmentActivity.
iOS uses LocalAuthentication with the biometric-only device-owner policy.
The system owns the prompt; the application never receives facial images,
fingerprint data, or biometric templates. Device passcode fallback is disabled.
Applications should offer their regular authenticated sign-in after cancellation,
lockout, or unavailable hardware. Never treat those results as success.

A second request during an active prompt returns Busy. Leaving the foreground
cancels the active request. Unknown or malformed bridge values fail closed.
All result codes are sequential integer enums defined by the PHP API and IDL.

This API confirms a biometric interaction. It does not cryptographically bind an
AuthVault read to that interaction. Applications must gate credential use and
navigation, hide sensitive content when backgrounded, and keep server-side
session expiration and revocation checks. Face ID requires the usage description
provided in the plugin manifest.

Validation before release must include PHP contracts, Android instrumentation,
iOS compilation, and system-prompt interaction on supported simulators/devices.
