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
class MeasurementHistoryFlowUiSmokeTest {
    private val screenshotDir = "/sdcard/Download/cellwatch-ui-flow/android/measurement-history-flow"

    @Test
    fun measurementHistoryFlow_showsLatestSnapshotAndSyncStatus() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(
            "Enable with -Pandroid.testInstrumentationRunnerArguments.cellwatchRunMeasurementHistoryUiSmoke=1",
            args.getString("cellwatchRunMeasurementHistoryUiSmoke") == "1",
        )
        grantHarnessRuntimePermissions()

        val runScenario = ActivityScenario.launch<MainActivity>(measurementRunIntent())
        try {
            onView(withId(MainActivity.MEASUREMENT_RUN_START_BUTTON_ID)).perform(click())
            assertLabelContainsAny(
                scenario = runScenario,
                id = MainActivity.MEASUREMENT_RUN_DETAIL_ID,
                expected = listOf("Measurement complete", "Measurement failed"),
                timeoutMs = 240_000,
            )
            onView(withId(MainActivity.MEASUREMENT_RUN_START_BUTTON_ID)).perform(click())
            assertLabelContainsAny(
                scenario = runScenario,
                id = MainActivity.MEASUREMENT_RUN_DETAIL_ID,
                expected = listOf("Measurement complete", "Measurement failed"),
                timeoutMs = 240_000,
            )
        } finally {
            runScenario.close()
        }

        val pendingScenario = ActivityScenario.launch<MainActivity>(pendingSyncIntent())
        try {
            drainPendingSyncToZero(pendingScenario, timeoutMs = 180_000)
        } finally {
            pendingScenario.close()
        }

        ensureScreenshotDir()
        val historyScenario = ActivityScenario.launch<MainActivity>(measurementHistoryIntent())
        try {
            assertLabelEquals(
                scenario = historyScenario,
                id = MainActivity.MEASUREMENT_HISTORY_STATE_KEY_ID,
                expected = "HAS_MEASUREMENT",
                timeoutMs = 30_000,
            )
            assertLabelContainsAny(
                scenario = historyScenario,
                id = MainActivity.MEASUREMENT_HISTORY_TITLE_ID,
                expected = listOf("Selected run"),
                timeoutMs = 30_000,
            )
            captureScreenshot("01-history-initial")
            assertLabelContainsAny(
                scenario = historyScenario,
                id = MainActivity.MEASUREMENT_HISTORY_RUN_1_ID,
                expected = listOf("Run 1:"),
                timeoutMs = 30_000,
            )
            onView(withId(MainActivity.MEASUREMENT_HISTORY_RUN_2_ID)).perform(click())
            assertLabelContainsAny(
                scenario = historyScenario,
                id = MainActivity.MEASUREMENT_RUN_RESULTS_ID,
                expected = listOf("Captured:", "Latency:", "Download:", "Upload:"),
                timeoutMs = 30_000,
            )
            captureScreenshot("02-history-multi")

            onView(withId(MainActivity.MEASUREMENT_HISTORY_REFRESH_ID)).perform(click())
            assertLabelContainsAny(
                scenario = historyScenario,
                id = MainActivity.MEASUREMENT_HISTORY_SYNC_STATE_KEY_ID,
                expected = listOf("PENDING", "SYNCED"),
                timeoutMs = 120_000,
            )
            assertLabelContainsAny(
                scenario = historyScenario,
                id = MainActivity.MEASUREMENT_HISTORY_SYNC_ID,
                expected = listOf("Pending sync queue:", "All records are synced."),
                timeoutMs = 15_000,
            )
            captureScreenshot("03-history-after-refresh")
        } finally {
            historyScenario.close()
        }
    }

    private fun measurementRunIntent(): Intent {
        return Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_UI_MODE, MainActivity.UI_MODE_MEASUREMENT_RUN_FLOW)
        }
    }

    private fun measurementHistoryIntent(): Intent {
        return Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_UI_MODE, MainActivity.UI_MODE_MEASUREMENT_HISTORY_FLOW)
        }
    }

    private fun pendingSyncIntent(): Intent {
        return Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_UI_MODE, MainActivity.UI_MODE_PENDING_SYNC_FLOW)
        }
    }

    private fun drainPendingSyncToZero(
        scenario: ActivityScenario<MainActivity>,
        timeoutMs: Long,
    ): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            onView(withId(MainActivity.RETRY_PENDING_SYNC_BUTTON_ID)).perform(click())
            val summary = waitForLabelUpdate(
                scenario = scenario,
                id = MainActivity.PENDING_SYNC_SUMMARY_ID,
                timeoutMs = 20_000,
            )
            if (summary.contains("Sync complete. No pending uploads.") || summary.contains("No pending uploads.")) {
                return true
            }
            Thread.sleep(750)
        }
        return false
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

    private fun waitForLabelUpdate(
        scenario: ActivityScenario<MainActivity>,
        id: Int,
        timeoutMs: Long,
    ): String {
        val deadline = System.currentTimeMillis() + timeoutMs
        var rendered = ""
        while (System.currentTimeMillis() < deadline) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            Thread.sleep(200)
            scenario.onActivity { activity ->
                rendered = activity.findViewById<android.widget.TextView>(id).text?.toString().orEmpty()
            }
            if (rendered.isNotBlank()) {
                return rendered
            }
        }
        return rendered
    }

    private fun assertLabelEquals(
        scenario: ActivityScenario<MainActivity>,
        id: Int,
        expected: String,
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
            if (rendered == expected) {
                return
            }
        }
        assertTrue("Expected label='$expected', got '$rendered'", rendered == expected)
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
