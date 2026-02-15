package edu.gatech.cc.cellwatch.androidtestapp

import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.content.Intent
import java.io.FileInputStream
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PendingSyncRetryUiSmokeTest {
    private val screenshotDir = "/sdcard/Download/cellwatch-ui-flow/android/pending-sync-retry"

    @Test
    fun pendingSyncRetry_rendersCountsAndFriendlySummary() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(
            "Enable with -Pandroid.testInstrumentationRunnerArguments.cellwatchRunPendingSyncUiSmoke=1",
            args.getString("cellwatchRunPendingSyncUiSmoke") == "1",
        )
        grantHarnessRuntimePermissions()

        val scenario = ActivityScenario.launch<MainActivity>(pendingSyncFlowIntent())
        try {
            ensureScreenshotDir()
            captureScreenshot("01-ready")
            onView(withId(MainActivity.PENDING_SYNC_COUNTS_BUTTON_ID)).perform(click())
            assertSummaryContainsAny(
                scenario = scenario,
                expected = listOf("Pending uploads:"),
            )
            captureScreenshot("02-counts")

            onView(withId(MainActivity.RETRY_PENDING_SYNC_BUTTON_ID)).perform(click())
            assertSummaryContainsAny(
                scenario = scenario,
                expected = listOf(
                    "Sync complete",
                    "Sync partially complete",
                    "Sync failed",
                    "Sync still pending",
                    "No pending uploads",
                ),
            )
            captureScreenshot("03-retry")

            var detail = ""
            scenario.onActivity { activity ->
                detail = activity.findViewById<android.widget.TextView>(
                    MainActivity.STATUS_TEXT_VIEW_ID,
                ).text?.toString().orEmpty()
            }
            assertTrue(
                "Expected user-facing pending sync detail, got: $detail",
                detail.isNotBlank() && !detail.contains("status=", ignoreCase = true),
            )
        } finally {
            scenario.close()
        }
    }

    private fun assertSummaryContainsAny(
        scenario: ActivityScenario<MainActivity>,
        expected: List<String>,
    ) {
        val deadline = System.currentTimeMillis() + 90_000
        var rendered = ""
        while (System.currentTimeMillis() < deadline) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            Thread.sleep(500)
            scenario.onActivity { activity ->
                rendered = activity.findViewById<android.widget.TextView>(
                    MainActivity.PENDING_SYNC_SUMMARY_ID,
                ).text?.toString().orEmpty()
            }
            if (expected.any { rendered.contains(it) }) {
                return
            }
        }
        assertTrue(
            "Expected summary text to contain one of '$expected', got: $rendered",
            expected.any { rendered.contains(it) },
        )
    }

    private fun pendingSyncFlowIntent(): Intent {
        return Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_UI_MODE, MainActivity.UI_MODE_PENDING_SYNC_FLOW)
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
