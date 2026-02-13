package edu.gatech.cc.cellwatch.androidtestapp

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.FileInputStream
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Phase3SequenceButtonUiSmokeTest {
    private val screenshotDir = "/sdcard/Download/cellwatch-ui-flow/android"

    @Test
    fun clickingPhase3Button_rendersPhase3Envelope() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(
            "Enable with -Pandroid.testInstrumentationRunnerArguments.cellwatchRunPhase3UiSmoke=1",
            args.getString("cellwatchRunPhase3UiSmoke") == "1",
        )

        grantHarnessRuntimePermissions()

        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            ensureScreenshotDir()
            captureScreenshot("01-ready")
            onView(withId(MainActivity.RUN_PHASE3_SEQUENCE_BUTTON_ID)).perform(click())

            val deadline = System.currentTimeMillis() + 120_000
            var rendered = ""
            while (System.currentTimeMillis() < deadline) {
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                Thread.sleep(500)
                scenario.onActivity { activity ->
                    rendered = activity.findViewById<android.widget.TextView>(
                        MainActivity.STATUS_TEXT_VIEW_ID,
                    ).text?.toString().orEmpty()
                }
                if (rendered.contains("smokeEnvelope scenario=phase3-sequence-sync")) {
                    break
                }
            }
            captureScreenshot("02-after-phase3")

            assertTrue(
                "Expected phase3 status envelope after button click, got: $rendered",
                rendered.contains("smokeEnvelope scenario=phase3-sequence-sync"),
            )
            assertTrue(
                "Expected success status from phase3 UI smoke, got: $rendered",
                rendered.contains("status=SUCCESS"),
            )
            assertFalse(
                "Phase3 UI smoke reported failure envelope: $rendered",
                rendered.contains("status=FAILURE"),
            )
            assertFalse(
                "Status should not remain initial ready message after phase3 click",
                rendered.contains("Ready. Local Supabase target is enforced by default."),
            )
        } finally {
            scenario.close()
        }
    }

    private fun ensureScreenshotDir() {
        runShell("mkdir -p $screenshotDir")
    }

    private fun captureScreenshot(name: String) {
        runShell("screencap -p $screenshotDir/$name.png")
    }

    private fun runShell(command: String) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val fd = automation.executeShellCommand(command)
        FileInputStream(fd.fileDescriptor).use { it.readBytes() }
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
                val fd = automation.executeShellCommand(
                    "pm grant edu.gatech.cc.cellwatch.androidtestapp $permission",
                )
                FileInputStream(fd.fileDescriptor).use { it.readBytes() }
            }
        } finally {
            automation.dropShellPermissionIdentity()
        }
    }
}
