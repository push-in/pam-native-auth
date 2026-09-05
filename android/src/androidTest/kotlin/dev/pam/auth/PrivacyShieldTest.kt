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
    fun nativeCoverObscuresRenderedWindowPixels() {
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        ActivityScenario.launch(PamActivity::class.java).use { scenario ->
            lateinit var shield: PrivacyShield
            scenario.onActivity { activity ->
                (activity.window.decorView as android.view.ViewGroup).addView(
                    android.view.View(activity).apply {
                        setBackgroundColor(android.graphics.Color.RED)
                        elevation = Float.MAX_VALUE / 2
                    }, android.view.ViewGroup.LayoutParams(-1, -1)
                )
            }
            instrumentation.waitForIdleSync()
            fun centerPixel(): Int {
                val screenshot = instrumentation.uiAutomation.takeScreenshot()
                assertNotNull(screenshot)
                return screenshot.getPixel(screenshot.width / 2, screenshot.height / 2).also { screenshot.recycle() }
            }
            fun awaitPixel(expected: Int) {
                val deadline = android.os.SystemClock.uptimeMillis() + 5000
                var actual: Int
                do {
                    actual = centerPixel()
                    if (actual == expected) return
                    android.os.SystemClock.sleep(50)
                } while (android.os.SystemClock.uptimeMillis() < deadline)
                assertEquals(expected, actual)
            }
            awaitPixel(android.graphics.Color.RED)
            scenario.onActivity { activity ->
                shield = PrivacyShield(activity)
                assertTrue(shield.reveal())
            }
            instrumentation.waitForIdleSync()
            instrumentation.uiAutomation.executeShellCommand("input keyevent 187").close()
            android.os.SystemClock.sleep(1500)
            val recents = instrumentation.uiAutomation.takeScreenshot()
            val context = instrumentation.targetContext
            java.io.File(context.cacheDir, "privacy-recents.png").outputStream().use {
                recents.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
            }
            var exposed = 0
            for (y in 0 until recents.height step 4) {
                for (x in 0 until recents.width step 4) {
                    val color = recents.getPixel(x, y)
                    if (android.graphics.Color.red(color) > 200 && android.graphics.Color.green(color) < 40 && android.graphics.Color.blue(color) < 40) exposed++
                }
            }
            recents.recycle()
            instrumentation.uiAutomation.executeShellCommand("input keyevent 4").close()
            instrumentation.waitForIdleSync()
            assertEquals("App switcher must not expose the reference content", 0, exposed)
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity { shield.conceal() }
            instrumentation.waitForIdleSync()
            awaitPixel(android.graphics.Color.BLACK)
            scenario.onActivity { shield.close() }
        }
    }

    @Test
    fun concealedContentIsRemovedFromAccessibilityAndRestored() {
        ActivityScenario.launch(PamActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val content = (activity.window.decorView as android.view.ViewGroup).getChildAt(0)
                val original = content.importantForAccessibility
                val shield = PrivacyShield(activity)
                assertEquals(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS, content.importantForAccessibility)
                assertTrue(shield.reveal())
                assertEquals(original, content.importantForAccessibility)
                shield.conceal()
                assertEquals(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS, content.importantForAccessibility)
                shield.close()
                assertEquals(original, content.importantForAccessibility)
            }
        }
    }

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
