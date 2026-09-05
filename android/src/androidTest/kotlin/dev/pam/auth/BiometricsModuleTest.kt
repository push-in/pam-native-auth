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
    fun closingPromptCompletesOnceAndRejectsConcurrentRequest() {
        val completed = CountDownLatch(1)
        val busy = CountDownLatch(1)
        val calls = AtomicInteger()
        ActivityScenario.launch(PamActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val module = BiometricsModule(activity)
                module.invoke("authenticate", payload) { _, data ->
                    calls.incrementAndGet()
                    assertEquals(WireValue.Integer(2), WireMap.decode(data)["state"])
                    completed.countDown()
                }
                module.invoke("authenticate", payload) { _, data ->
                    assertEquals(WireValue.Integer(6), WireMap.decode(data)["state"])
                    busy.countDown()
                }
                module.close()
            }
            assertTrue(completed.await(5, TimeUnit.SECONDS))
            assertTrue(busy.await(5, TimeUnit.SECONDS))
            assertEquals(1, calls.get())
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
}
