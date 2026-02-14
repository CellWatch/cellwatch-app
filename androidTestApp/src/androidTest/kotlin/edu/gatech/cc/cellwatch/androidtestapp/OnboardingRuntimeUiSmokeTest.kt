package edu.gatech.cc.cellwatch.androidtestapp

import android.content.Context
import android.content.Intent
import android.widget.EditText
import androidx.test.core.app.ApplicationProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import edu.gatech.cc.cellwatch.androidtestapp.onboarding.AndroidOnboardingProfileStore
import java.io.FileInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnboardingRuntimeUiSmokeTest {
    private val screenshotDir = "/sdcard/Download/cellwatch-ui-flow/android/onboarding-profile-entry"

    @Test
    fun onboardingProfileEntry_rendersSuccessAfterStepwiseInput() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(
            "Enable with -Pandroid.testInstrumentationRunnerArguments.cellwatchRunOnboardingUiSmoke=1",
            args.getString("cellwatchRunOnboardingUiSmoke") == "1",
        )
        grantHarnessRuntimePermissions()
        clearPersistedOnboardingProfile()

        val initialScenario = ActivityScenario.launch<MainActivity>(onboardingFlowIntent())
        try {
            ensureScreenshotDir()
            captureScreenshot("01-ready")
            onView(withId(MainActivity.ONBOARDING_NAME_INPUT_ID)).perform(replaceText("Jane Doe"), closeSoftKeyboard())
            captureScreenshot("02-after-name")
            onView(withId(MainActivity.ONBOARDING_PHONE_INPUT_ID)).perform(replaceText("4045551212"), closeSoftKeyboard())
            captureScreenshot("03-after-phone")
            onView(withId(MainActivity.ONBOARDING_EMAIL_INPUT_ID)).perform(replaceText("jane@example.com"), closeSoftKeyboard())
            captureScreenshot("04-after-email")
            onView(withId(MainActivity.ONBOARDING_ACK_CHECKBOX_ID)).perform(click())
            captureScreenshot("05-after-ack")
            onView(withId(MainActivity.ONBOARDING_SUBMIT_BUTTON_ID)).perform(click())
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            Thread.sleep(300)
            captureScreenshot("06-after-submit")

            var rendered = ""
            initialScenario.onActivity { activity ->
                rendered = activity.findViewById<android.widget.TextView>(
                    MainActivity.STATUS_TEXT_VIEW_ID,
                ).text?.toString().orEmpty()
            }
            assertTrue(
                "Expected onboarding success status text, got: $rendered",
                rendered.contains("Onboarding submit=SUCCESS"),
            )
            assertTrue(
                "Expected normalized onboarding details in status text, got: $rendered",
                rendered.contains("phone=404-555-1212") && rendered.contains("onboardingComplete=true"),
            )
        } finally {
            initialScenario.close()
        }

        val reopenScenario = ActivityScenario.launch<MainActivity>(onboardingFlowIntent())
        try {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            captureScreenshot("07-reopen-prefilled")

            var prefilledName = ""
            var prefilledPhone = ""
            var prefilledEmail = ""
            var prefilledAck = false
            reopenScenario.onActivity { activity ->
                prefilledName = activity.findViewById<EditText>(MainActivity.ONBOARDING_NAME_INPUT_ID).text.toString()
                prefilledPhone = activity.findViewById<EditText>(MainActivity.ONBOARDING_PHONE_INPUT_ID).text.toString()
                prefilledEmail = activity.findViewById<EditText>(MainActivity.ONBOARDING_EMAIL_INPUT_ID).text.toString()
                prefilledAck = activity.findViewById<android.widget.CheckBox>(MainActivity.ONBOARDING_ACK_CHECKBOX_ID).isChecked
            }
            assertEquals("Jane Doe", prefilledName)
            assertEquals("404-555-1212", prefilledPhone)
            assertEquals("jane@example.com", prefilledEmail)
            assertTrue(prefilledAck)

            onView(withId(MainActivity.ONBOARDING_PHONE_INPUT_ID)).perform(replaceText("4041112222"), closeSoftKeyboard())
            onView(withId(MainActivity.ONBOARDING_EMAIL_INPUT_ID)).perform(replaceText("updated@example.com"), closeSoftKeyboard())
            captureScreenshot("08-after-edit")
            onView(withId(MainActivity.ONBOARDING_SUBMIT_BUTTON_ID)).perform(click())
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            Thread.sleep(300)
            captureScreenshot("09-after-edit-submit")

            var editRendered = ""
            reopenScenario.onActivity { activity ->
                editRendered = activity.findViewById<android.widget.TextView>(
                    MainActivity.STATUS_TEXT_VIEW_ID,
                ).text?.toString().orEmpty()
            }
            assertTrue(
                "Expected onboarding edit success status text, got: $editRendered",
                editRendered.contains("Onboarding submit=SUCCESS"),
            )
            assertTrue(
                "Expected updated profile values in status text, got: $editRendered",
                editRendered.contains("phone=404-111-2222") && editRendered.contains("email=updated@example.com"),
            )
        } finally {
            reopenScenario.close()
        }

        val verifyUpdatedScenario = ActivityScenario.launch<MainActivity>(onboardingFlowIntent())
        try {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            captureScreenshot("10-reopen-after-edit")

            var updatedPhone = ""
            var updatedEmail = ""
            verifyUpdatedScenario.onActivity { activity ->
                updatedPhone = activity.findViewById<EditText>(MainActivity.ONBOARDING_PHONE_INPUT_ID).text.toString()
                updatedEmail = activity.findViewById<EditText>(MainActivity.ONBOARDING_EMAIL_INPUT_ID).text.toString()
            }
            assertEquals("404-111-2222", updatedPhone)
            assertEquals("updated@example.com", updatedEmail)
        } finally {
            verifyUpdatedScenario.close()
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

    private fun clearPersistedOnboardingProfile() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences(AndroidOnboardingProfileStore.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    private fun onboardingFlowIntent(): Intent {
        return Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_UI_MODE, MainActivity.UI_MODE_ONBOARDING_FLOW)
        }
    }
}
