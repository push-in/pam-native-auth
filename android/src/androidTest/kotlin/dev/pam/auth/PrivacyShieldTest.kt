package dev.pam.auth

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.pam.nativeapp.PamActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PrivacyShieldTest {
    @Test
    fun missingActivityCannotAuthorizeDisplay() {
        val complete = java.util.concurrent.CountDownLatch(1)
        ScreenPrivacyModule(androidx.test.core.app.ApplicationProvider.getApplicationContext()).invoke(
            "reveal", dev.pam.nativeapp.protocol.WireMap.encode(emptyMap())
        ) { _, payload ->
            assertEquals(dev.pam.nativeapp.protocol.WireValue.Integer(3), dev.pam.nativeapp.protocol.WireMap.decode(payload)["state"])
            complete.countDown()
        }
        assertTrue(complete.await(5, java.util.concurrent.TimeUnit.SECONDS))
    }

    @Test
    fun returningToForegroundDoesNotRevealWithoutAuthorization() {
        ActivityScenario.launch(PamActivity::class.java).use { scenario ->
            lateinit var shield: PrivacyShield
            scenario.onActivity { activity ->
                shield = PrivacyShield(activity)
                assertTrue(shield.concealed)
                assertTrue(shield.reveal())
                assertFalse(shield.concealed)
            }
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.onActivity {
                assertTrue(shield.concealed)
                assertFalse(shield.reveal())
            }
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity {
                assertTrue(shield.concealed)
                assertTrue(shield.reveal())
                shield.close()
                assertFalse(shield.reveal())
                shield.close()
            }
        }
    }
}
