package edu.gatech.cc.cellwatch.androidtestapp

import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.FileInputStream
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MeasurementStartPreflightUiSmokeTest {
    private val screenshotDir = "/sdcard/Download/cellwatch-ui-flow/android/measurement-start-preflight"

    @Test
    fun measurementStartPreflight_rendersReasonCodesAndConfirmGate() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(
            "Enable with -Pandroid.testInstrumentationRunnerArguments.cellwatchRunMeasurementStartPreflightUiSmoke=1",
            args.getString("cellwatchRunMeasurementStartPreflightUiSmoke") == "1",
        )
        grantHarnessRuntimePermissions()

        val scenario = ActivityScenario.launch<MainActivity>(
            measurementStartFlowIntent(networkPathOverride = "wifi"),
        )
        try {
            ensureScreenshotDir()
            captureScreenshot("01-ready")

            onView(withId(MainActivity.MEASUREMENT_PREFLIGHT_EVALUATE_BUTTON_ID)).perform(click())
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            captureScreenshot("02-wifi-warning-dialog")
            onView(withText("Measure anyway")).perform(click())
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            captureScreenshot("03-wifi-confirmed-allowed")
            assertOutputContainsLabel(
                scenario,
                "Preflight passed. You can start measuring.",
            )
        } finally {
            scenario.close()
        }

        val unknownScenario = ActivityScenario.launch<MainActivity>(
            measurementStartFlowIntent(networkPathOverride = "unknown"),
        )
        try {
            onView(withId(MainActivity.MEASUREMENT_PREFLIGHT_EVALUATE_BUTTON_ID)).perform(click())
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            captureScreenshot("04-unknown-warning-dialog")
            onView(withText("Cancel")).perform(click())
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            assertOutputContainsLabel(
                unknownScenario,
                "Wi-Fi detected. Choose Measure anyway or Cancel.",
            )
        } finally {
            unknownScenario.close()
        }
    }

    private fun assertOutputContainsLabel(scenario: ActivityScenario<MainActivity>, expectedText: String) {
        var rendered = ""
        scenario.onActivity { activity ->
            rendered = activity.findViewById<android.widget.TextView>(
                MainActivity.MEASUREMENT_PREFLIGHT_OUTPUT_ID,
            ).text?.toString().orEmpty()
        }
        assertTrue("Expected preflight output to contain '$expectedText', got: $rendered", rendered.contains(expectedText))
    }

    private fun measurementStartFlowIntent(networkPathOverride: String): Intent {
        return Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_UI_MODE, MainActivity.UI_MODE_MEASUREMENT_START_FLOW)
            putExtra(MainActivity.EXTRA_MEASUREMENT_PREFLIGHT_OVERRIDE_NETWORK_PATH, networkPathOverride)
            putExtra(MainActivity.EXTRA_MEASUREMENT_PREFLIGHT_OVERRIDE_LOCATION_PERMISSION, true)
            putExtra(MainActivity.EXTRA_MEASUREMENT_PREFLIGHT_OVERRIDE_RUNTIME_PROFILE, true)
            putExtra(MainActivity.EXTRA_MEASUREMENT_PREFLIGHT_OVERRIDE_COLLECTION_MODE, "fcc_challenge")
        }
    }

    private fun ensureScreenshotDir() {
        runShell("rm -rf $screenshotDir && mkdir -p $screenshotDir")
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
