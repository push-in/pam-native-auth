package dev.pam.auth

import androidx.lifecycle.Lifecycle
import androidx.biometric.BiometricManager
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pam.nativeapp.PamActivity
import dev.pam.nativeapp.modules.ModuleResultStatus
import dev.pam.nativeapp.protocol.WireMap
import dev.pam.nativeapp.protocol.WireValue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BiometricsModuleTest {
    private val payload = WireMap.encode(mapOf("reason" to WireValue.Text("PAM biometric certification"), "cancelLabel" to WireValue.Text("Cancel test")))

    @Test
    fun platformPromptErrorsMapToEveryOutcome() {
        val prompt = android.hardware.biometrics.BiometricPrompt::class.java
        fun code(name: String) = prompt.getField(name).getInt(null)
        val expected = mapOf(
            "BIOMETRIC_ERROR_USER_CANCELED" to 2L,
            "BIOMETRIC_ERROR_CANCELED" to 2L,
            "BIOMETRIC_ERROR_LOCKOUT" to 4L,
            "BIOMETRIC_ERROR_LOCKOUT_PERMANENT" to 4L,
            "BIOMETRIC_ERROR_NO_BIOMETRICS" to 3L,
            "BIOMETRIC_ERROR_HW_NOT_PRESENT" to 3L,
            "BIOMETRIC_ERROR_HW_UNAVAILABLE" to 3L,
            "BIOMETRIC_ERROR_TIMEOUT" to 5L,
            "BIOMETRIC_ERROR_UNABLE_TO_PROCESS" to 5L,
        )
        for ((name, result) in expected) {
            assertEquals(name, result, BiometricsModule.platformResult(code(name)).code)
        }
    }

    @Test
    fun closedModuleCannotOpenAnotherPrompt() {
        val completed = CountDownLatch(2)
        ActivityScenario.launch(PamActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val module = BiometricsModule(activity)
                module.close()
                module.close()
                for (method in listOf("authenticate", "availability")) {
                    module.invoke(method, payload) { status, data ->
                        assertEquals(ModuleResultStatus.SUCCESS, status)
                        val key = if (method == "availability") "availability" else "state"
                        assertEquals(WireValue.Integer(3), WireMap.decode(data)[key])
                        completed.countDown()
                    }
                }
            }
            assertTrue(completed.await(5, TimeUnit.SECONDS))
        }
    }

    @Test
    fun applicationContextCannotAuthenticate() {
        val completed = CountDownLatch(1)
        BiometricsModule(ApplicationProvider.getApplicationContext()).invoke("authenticate", payload) { status, data ->
            assertEquals(ModuleResultStatus.SUCCESS, status)
            assertEquals(WireValue.Integer(3), WireMap.decode(data)["state"])
            completed.countDown()
        }
        assertTrue(completed.await(5, TimeUnit.SECONDS))
    }

    @Test
    fun aNewRequestReplacesTheActivePromptAndEachCompletesOnce() {
        val first = CountDownLatch(1)
        val second = CountDownLatch(1)
        val firstCalls = AtomicInteger()
        val secondCalls = AtomicInteger()
        ActivityScenario.launch(PamActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val module = BiometricsModule(activity)
                module.invoke("authenticate", payload) { _, data ->
                    firstCalls.incrementAndGet()
                    // Replaced by the second request (a re-mounted screen after a hot reload).
                    assertEquals(WireValue.Integer(2), WireMap.decode(data)["state"])
                    first.countDown()
                }
                module.invoke("authenticate", payload) { _, data ->
                    secondCalls.incrementAndGet()
                    assertNotEquals(WireValue.Integer(6), WireMap.decode(data)["state"])
                    second.countDown()
                }
                module.close()
            }
            assertTrue(first.await(5, TimeUnit.SECONDS))
            assertTrue(second.await(5, TimeUnit.SECONDS))
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            assertEquals(1, firstCalls.get())
            assertEquals(1, secondCalls.get())
        }
    }

    @Test
    fun aHotReloadClosesTheOpenPrompt() {
        val completed = CountDownLatch(1)
        ActivityScenario.launch(PamActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                BiometricsModule(activity).invoke("authenticate", payload) { _, data ->
                    // Cancelled before the PHP request restarts; or Unavailable
                    // when the device has no biometrics to show a prompt for.
                    assertTrue(WireMap.decode(data)["state"] in setOf(WireValue.Integer(2), WireValue.Integer(3)))
                    completed.countDown()
                }
            }
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            dev.pam.nativeapp.HostEvents.prepareReload()
            assertTrue(completed.await(5, TimeUnit.SECONDS))
        }
    }

    @Test
    fun backgroundingCancelsPromptOnce() {
        assumeTrue(BiometricManager.from(ApplicationProvider.getApplicationContext()).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS)
        val completed = CountDownLatch(1)
        val calls = AtomicInteger()
        var result: WireValue? = null
        ActivityScenario.launch(PamActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                BiometricsModule(activity).invoke("authenticate", payload) { _, data ->
                    calls.incrementAndGet()
                    result = WireMap.decode(data)["state"]
                    completed.countDown()
                }
            }
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.moveToState(Lifecycle.State.CREATED)
            assertTrue(completed.await(5, TimeUnit.SECONDS))
            assertEquals(WireValue.Integer(2), result)
            scenario.moveToState(Lifecycle.State.RESUMED)
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            assertEquals(1, calls.get())
        }
    }

    /** Enroll emulator fingerprint 1, then send `adb emu finger touch 1` while this test waits. */
    @Test
    fun enrolledFingerprintAuthenticatesThroughSystemPrompt() {
        assumeTrue(BiometricManager.from(ApplicationProvider.getApplicationContext()).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS)
        val completed = CountDownLatch(1)
        var result: WireValue? = null
        ActivityScenario.launch(PamActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                BiometricsModule(activity).invoke("authenticate", payload) { _, data ->
                    result = WireMap.decode(data)["state"]
                    completed.countDown()
                }
            }
            assertTrue("Send enrolled fingerprint 1 to the emulator", completed.await(45, TimeUnit.SECONDS))
            assertEquals(WireValue.Integer(1), result)
        }
    }

    @Test
    fun legacyCompatErrorsMapToBiometricErrors() {
        val prompt = androidx.biometric.BiometricPrompt::class.java
        fun code(name: String) = prompt.getField(name).getInt(null)
        val expected = mapOf(
            "ERROR_LOCKOUT" to 4,
            "ERROR_LOCKOUT_PERMANENT" to 4,
            "ERROR_HW_NOT_PRESENT" to 3,
            "ERROR_HW_UNAVAILABLE" to 3,
            "ERROR_NO_BIOMETRICS" to 3,
            "ERROR_NEGATIVE_BUTTON" to 2,
            "ERROR_USER_CANCELED" to 2,
            "ERROR_CANCELED" to 2,
            "ERROR_TIMEOUT" to 2,
        )
        for ((name, error) in expected) {
            assertEquals(name, error, PlatformBiometricsCompatibility.compatError(code(name)))
        }
    }

    /** Android 7.0-9: legacy status follows androidx (weak biometric) instead of always reporting none. */
    @Test
    fun legacyStatusOnAndroid7To9FollowsAndroidxAvailability() {
        assumeTrue(android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q)
        val enrolled = BiometricManager.from(ApplicationProvider.getApplicationContext())
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
        val completed = CountDownLatch(1)
        var values: Map<String, WireValue> = emptyMap()
        ActivityScenario.launch(PamActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                LegacyBiometricsModule(activity).invoke("status", ByteArray(0)) { _, data ->
                    values = WireMap.decode(data)
                    completed.countDown()
                }
            }
            assertTrue(completed.await(10, TimeUnit.SECONDS))
        }
        assertEquals(WireValue.Flag(enrolled), values["available"])
        assertEquals(WireValue.Integer(if (enrolled) 2 else 1), values["kind"])
    }

    /** Android 7.0-9 with fingerprint 1 enrolled: send `adb emu finger touch 1` while this test waits. */
    @Test
    fun legacyFingerprintAuthenticatesOnAndroid7To9() {
        assumeTrue(android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q)
        assumeTrue(BiometricManager.from(ApplicationProvider.getApplicationContext()).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS)
        val completed = CountDownLatch(1)
        var values: Map<String, WireValue> = emptyMap()
        val request = WireMap.encode(mapOf("title" to WireValue.Text("PAM legacy biometric"), "cancelLabel" to WireValue.Text("Cancel test")))
        ActivityScenario.launch(PamActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                LegacyBiometricsModule(activity).invoke("authenticate", request) { _, data ->
                    values = WireMap.decode(data)
                    completed.countDown()
                }
            }
            assertTrue("Send enrolled fingerprint 1 to the emulator", completed.await(45, TimeUnit.SECONDS))
        }
        assertEquals(WireValue.Flag(true), values["authenticated"])
    }
}
