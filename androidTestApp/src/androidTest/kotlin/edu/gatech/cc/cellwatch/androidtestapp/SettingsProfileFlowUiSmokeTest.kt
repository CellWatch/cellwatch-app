package edu.gatech.cc.cellwatch.androidtestapp

import android.content.Context
import android.content.Intent
import android.widget.EditText
import android.widget.RadioButton
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
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
class SettingsProfileFlowUiSmokeTest {
    private val screenshotDir = "/sdcard/Download/cellwatch-ui-flow/android/settings-profile-flow"

    @Test
    fun settingsProfileFlow_persistsCollectionModeAndProfileFields() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(
            "Enable with -Pandroid.testInstrumentationRunnerArguments.cellwatchRunSettingsProfileUiSmoke=1",
            args.getString("cellwatchRunSettingsProfileUiSmoke") == "1",
        )
        grantHarnessRuntimePermissions()
        clearPersistedOnboardingProfile()

        val initialScenario = ActivityScenario.launch<MainActivity>(settingsIntent())
        try {
            ensureScreenshotDir()
            captureScreenshot("01-ready")

            onView(withId(MainActivity.SETTINGS_MODE_FCC_ID)).perform(click())
            captureScreenshot("02-mode-fcc")

            onView(withId(MainActivity.ONBOARDING_NAME_INPUT_ID)).perform(replaceText("Jane Doe"), closeSoftKeyboard())
            onView(withId(MainActivity.ONBOARDING_PHONE_INPUT_ID)).perform(replaceText("4045551212"), closeSoftKeyboard())
            onView(withId(MainActivity.ONBOARDING_EMAIL_INPUT_ID)).perform(replaceText("jane@example.com"), closeSoftKeyboard())
            onView(withId(MainActivity.ONBOARDING_ACK_CHECKBOX_ID)).perform(click())
            captureScreenshot("03-filled")

            onView(withId(MainActivity.SETTINGS_COPY_DEVICE_ID_BUTTON_ID)).perform(click())
            onView(withId(MainActivity.SETTINGS_COPY_APP_VERSION_BUTTON_ID)).perform(click())
            captureScreenshot("04-after-copy")

            onView(withId(MainActivity.SETTINGS_SUBMIT_BUTTON_ID)).perform(click())
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            Thread.sleep(300)
            captureScreenshot("05-after-save")

            var rendered = ""
            var copiedDeviceId = ""
            var copiedVersion = ""
            initialScenario.onActivity { activity ->
                rendered = activity.findViewById<android.widget.TextView>(
                    MainActivity.STATUS_TEXT_VIEW_ID,
                ).text?.toString().orEmpty()
                copiedDeviceId = activity.findViewById<android.widget.TextView>(
                    MainActivity.SETTINGS_DEVICE_ID_VALUE_ID,
                ).text?.toString().orEmpty()
                copiedVersion = activity.findViewById<android.widget.TextView>(
                    MainActivity.SETTINGS_APP_VERSION_VALUE_ID,
                ).text?.toString().orEmpty()
            }
            assertTrue(rendered.contains("Settings save=SUCCESS"))
            assertTrue(rendered.contains("collectionMode=FCC_CHALLENGE"))
            assertTrue(copiedDeviceId.isNotBlank())
            assertTrue(copiedVersion.isNotBlank())
        } finally {
            initialScenario.close()
        }

        val reopenScenario = ActivityScenario.launch<MainActivity>(settingsIntent())
        try {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            captureScreenshot("06-reopen-saved")
            var prefilledName = ""
            var prefilledPhone = ""
            var prefilledEmail = ""
            var fccChecked = false
            reopenScenario.onActivity { activity ->
                prefilledName = activity.findViewById<EditText>(MainActivity.ONBOARDING_NAME_INPUT_ID).text.toString()
                prefilledPhone = activity.findViewById<EditText>(MainActivity.ONBOARDING_PHONE_INPUT_ID).text.toString()
                prefilledEmail = activity.findViewById<EditText>(MainActivity.ONBOARDING_EMAIL_INPUT_ID).text.toString()
                fccChecked = activity.findViewById<RadioButton>(MainActivity.SETTINGS_MODE_FCC_ID).isChecked
            }
            assertEquals("Jane Doe", prefilledName)
            assertEquals("404-555-1212", prefilledPhone)
            assertEquals("jane@example.com", prefilledEmail)
            assertTrue(fccChecked)
        } finally {
            reopenScenario.close()
        }
    }

    private fun settingsIntent(): Intent {
        return Intent(ApplicationProvider.getApplicationContext(), MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_UI_MODE, MainActivity.UI_MODE_SETTINGS_PROFILE_FLOW)
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

    private fun clearPersistedOnboardingProfile() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences(AndroidOnboardingProfileStore.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }
}
