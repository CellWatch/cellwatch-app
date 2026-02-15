package edu.gatech.cc.cellwatch.androidtestapp

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.FileInputStream
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MeasurementRunFlowUiSmokeTest {
    private val screenshotDir = "/sdcard/Download/cellwatch-ui-flow/android/measurement-run-flow"

    @Test
    fun measurementRunFlow_showsLiveProgressAndCompletionState() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(
            "Enable with -Pandroid.testInstrumentationRunnerArguments.cellwatchRunMeasurementRunUiSmoke=1",
            args.getString("cellwatchRunMeasurementRunUiSmoke") == "1",
        )
        grantHarnessRuntimePermissions()

        val scenario = ActivityScenario.launch<MainActivity>(measurementRunFlowIntent())
        try {
            ensureScreenshotDir()
            captureScreenshot("01-ready")
            onView(withId(MainActivity.MEASUREMENT_RUN_START_BUTTON_ID)).perform(click())

            assertHeaderContains(
                scenario = scenario,
                expected = "Finding server",
            )
            captureScreenshot("02-finding-server")

            assertHeaderContains(
                scenario = scenario,
                expected = "Measuring latency",
            )
            captureScreenshot("03-running-latency")

            assertHeaderContainsAny(
                scenario = scenario,
                expected = listOf("Measuring download speed", "Measuring upload speed"),
            )
            captureScreenshot("04-running-throughput")

            assertDetailContainsAny(
                scenario = scenario,
                expected = listOf(
                    "Measurement complete",
                    "Measurement failed",
                ),
            )
            captureScreenshot("05-after-run")
            assertResultsContains(scenario, "Latency:")
            assertResultsContains(scenario, "Download:")
            assertResultsContains(scenario, "Upload:")

            onView(withId(MainActivity.MEASUREMENT_RUN_START_BUTTON_ID)).perform(click())
            assertHeaderContainsAny(
                scenario = scenario,
                expected = listOf(
                    "Finding server",
                    "Measuring latency",
                    "Measuring download speed",
                    "Measuring upload speed",
                    "Measurement complete",
                ),
            )
            captureScreenshot("06-after-take-another")

        } finally {
            scenario.close()
        }
    }

    private fun assertHeaderContains(
        scenario: ActivityScenario<MainActivity>,
        expected: String,
    ) {
        assertLabelContainsAny(
            scenario = scenario,
            id = MainActivity.MEASUREMENT_RUN_HEADER_ID,
            expected = listOf(expected),
            timeoutMs = 150_000,
        )
    }

    private fun assertHeaderContainsAny(
        scenario: ActivityScenario<MainActivity>,
        expected: List<String>,
    ) {
        assertLabelContainsAny(
            scenario = scenario,
            id = MainActivity.MEASUREMENT_RUN_HEADER_ID,
            expected = expected,
            timeoutMs = 150_000,
        )
    }

    private fun assertDetailContainsAny(
        scenario: ActivityScenario<MainActivity>,
        expected: List<String>,
    ) {
        assertLabelContainsAny(
            scenario = scenario,
            id = MainActivity.MEASUREMENT_RUN_DETAIL_ID,
            expected = expected,
            timeoutMs = 240_000,
        )
    }

    private fun assertResultsContains(
        scenario: ActivityScenario<MainActivity>,
        expected: String,
    ) {
        assertLabelContainsAny(
            scenario = scenario,
            id = MainActivity.MEASUREMENT_RUN_RESULTS_ID,
            expected = listOf(expected),
            timeoutMs = 30_000,
        )
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

    private fun measurementRunFlowIntent(): Intent {
        return Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_UI_MODE, MainActivity.UI_MODE_MEASUREMENT_RUN_FLOW)
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
