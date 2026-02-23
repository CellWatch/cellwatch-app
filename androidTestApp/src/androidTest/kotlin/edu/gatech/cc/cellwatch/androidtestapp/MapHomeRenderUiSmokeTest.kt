package edu.gatech.cc.cellwatch.androidtestapp

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.FileInputStream
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MapHomeRenderUiSmokeTest {
    private val screenshotDir = "/sdcard/Download/cellwatch-ui-flow/android/map-home-render"

    @Test
    fun mapHomeFlow_rendersMapStyleAndCapturesEvidence() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(
            "Enable with -Pandroid.testInstrumentationRunnerArguments.cellwatchRunMapHomeRenderUiSmoke=1",
            args.getString("cellwatchRunMapHomeRenderUiSmoke") == "1",
        )

        grantHarnessRuntimePermissions()
        ensureScreenshotDir()

        val scenario = ActivityScenario.launch<MainActivity>(mapHomeIntent())
        try {
            Thread.sleep(5_000)
            captureScreenshot("01-map-home-initial")
            assertLabelContainsAny(
                scenario = scenario,
                id = MainActivity.MAP_HOME_RENDER_STATE_ID,
                expected = listOf("STYLE_LOADED", "MAP_IDLE", "MAP_TIMEOUT"),
                timeoutMs = 45_000,
            )
            val finalState = readLabel(
                scenario = scenario,
                id = MainActivity.MAP_HOME_RENDER_STATE_ID,
            )
            captureScreenshot("02-map-home-state")
            assertTrue(
                "Expected map render state to reach STYLE_LOADED or MAP_IDLE, got: $finalState",
                finalState == "STYLE_LOADED" || finalState == "MAP_IDLE",
            )
        } finally {
            scenario.close()
        }
    }

    private fun mapHomeIntent(): Intent {
        return Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_UI_MODE, MainActivity.UI_MODE_MAP_HOME)
        }
    }

    private fun assertLabelContainsAny(
        scenario: ActivityScenario<MainActivity>,
        id: Int,
        expected: List<String>,
        timeoutMs: Long,
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        var rendered = ""
        while (System.currentTimeMillis() < deadline) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            Thread.sleep(200)
            scenario.onActivity { activity ->
                rendered = activity.findViewById<android.widget.TextView>(id).text?.toString().orEmpty()
            }
            if (expected.any { rendered.contains(it) }) {
                return
            }
        }
        assertTrue(
            "Expected label to contain one of '$expected', got: $rendered",
            expected.any { rendered.contains(it) },
        )
    }

    private fun readLabel(
        scenario: ActivityScenario<MainActivity>,
        id: Int,
    ): String {
        var rendered = ""
        scenario.onActivity { activity ->
            rendered = activity.findViewById<android.widget.TextView>(id).text?.toString().orEmpty()
        }
        return rendered
    }

    private fun ensureScreenshotDir() {
        runShell("mkdir -p $screenshotDir")
    }

    private fun captureScreenshot(name: String) {
        runShell("screencap -p $screenshotDir/$name.png")
    }

    private fun runShell(command: String) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.executeShellCommand(command).use { fd ->
            FileInputStream(fd.fileDescriptor).use { it.readBytes() }
        }
    }

    private fun grantHarnessRuntimePermissions() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.adoptShellPermissionIdentity()
        try {
            listOf(
                "android.permission.READ_PHONE_STATE",
                "android.permission.ACCESS_COARSE_LOCATION",
                "android.permission.ACCESS_FINE_LOCATION",
            ).forEach { permission ->
                automation.executeShellCommand(
                    "pm grant edu.gatech.cc.cellwatch.androidtestapp $permission",
                ).use { fd ->
                    FileInputStream(fd.fileDescriptor).use { it.readBytes() }
                }
            }
        } finally {
            automation.dropShellPermissionIdentity()
        }
    }
}
