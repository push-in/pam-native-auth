# Android plugin lifecycle compatibility

Auth 0.3.0 passed instrumentation when its sources were compiled directly in the isolated host app, but failed when PAM compiled it as its own generated Android library module. DefaultLifecycleObserver was unavailable on that module's compile classpath.

The fix replaces DefaultLifecycleObserver with LifecycleEventObserver, preserving ON_STOP biometric cancellation and ON_PAUSE/ON_STOP concealment plus ON_DESTROY cleanup. No app native source or vendor patch is permitted.

Validation still required: compile upstream sources as a separate plugin library using the generated module dependencies, then run the existing lifecycle and privacy instrumentation. Publish a patch release and install it in Linkinpay before restarting pam dev. The failed Linkinpay dev process was session 60830 and is terminal; there is no live hot-reload server from that process.

## Verified fix

Separate library compilation passed using the generated module configuration (`api(project(":plugin-api"))` plus biometric 1.1.0), against the upstream corrected sources. This reproduces the compile-classpath boundary missed by the original host-only test. Log: `/tmp/pam-auth-plugin-compile.log`.

Seven Android API 36 instrumentation cases passed after the change: all four privacy cases and three biometric lifecycle cases. Log: `/tmp/pam-auth-lifecycle-fix-tests.log`. iOS/PHP sources and dependency declarations are unchanged by this patch.
