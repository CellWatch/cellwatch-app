package edu.gatech.cc.cellwatch.ui.main

import androidx.annotation.StringRes
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.RootMatchers.isPlatformPopup
import androidx.test.espresso.matcher.ViewMatchers.Visibility
import androidx.test.espresso.matcher.ViewMatchers.hasErrorText
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import edu.gatech.cc.cellwatch.BuildConfig
import edu.gatech.cc.cellwatch.R
import edu.gatech.cc.cellwatch.data.model.CollectionMode
import edu.gatech.cc.cellwatch.ui.UiTestSupport.EMAIL
import edu.gatech.cc.cellwatch.ui.UiTestSupport.NAME
import edu.gatech.cc.cellwatch.ui.UiTestSupport.PHONE
import edu.gatech.cc.cellwatch.ui.UiTestSupport.eventually
import edu.gatech.cc.cellwatch.ui.UiTestSupport.finishAllActivities
import edu.gatech.cc.cellwatch.ui.UiTestSupport.resetSettings
import edu.gatech.cc.cellwatch.ui.UiTestSupport.saveContactDetails
import edu.gatech.cc.cellwatch.ui.UiTestSupport.setting
import edu.gatech.cc.cellwatch.ui.UiTestSupport.settings
import edu.gatech.cc.cellwatch.ui.UiTestSupport.useFccChallengeMode
import org.hamcrest.Matchers.not
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Settings: the contact details the FCC needs for a challenge (§5.1.1 of the FCC's mobile speed
 * test data spec, July 28, 2025), the collection mode, and the app version and device ID that
 * each submission carries (§5.1.2).
 */
@RunWith(AndroidJUnit4::class)
class SettingsActivityTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun firstRun() = resetSettings()

    @After
    fun closeScreens() = finishAllActivities()

    @Test
    fun showsTheAppVersionAndDeviceId() {
        // §5.1.2: app_version has to be a version the FCC approved, and it approved 1.* for
        // CellWatch. device_id identifies the installation.
        assertTrue(BuildConfig.VERSION_NAME.startsWith("1."))
        val deviceId = setting { settings.getDeviceId() }

        ActivityScenario.launch(SettingsActivity::class.java).use {
            onView(withId(R.id.appVersion)).perform(scrollTo()).check(matches(withText(BuildConfig.VERSION_NAME)))
            eventually { onView(withId(R.id.deviceId)).check(matches(withText(deviceId))) }
        }
    }

    @Test
    fun contactDetailsAreLockedUntilEdit() {
        ActivityScenario.launch(SettingsActivity::class.java).use {
            onView(withId(R.id.nameEditText)).check(matches(not(isEnabled())))
            onView(withId(R.id.submit_edit)).check(matches(withEffectiveVisibility(Visibility.GONE)))

            onView(withId(R.id.settingsEditButton)).perform(click())

            onView(withId(R.id.nameEditText)).check(matches(isEnabled()))
            onView(withId(R.id.phoneEditText)).check(matches(isEnabled()))
            onView(withId(R.id.emailEditText)).check(matches(isEnabled()))
            onView(withId(R.id.submit_edit)).check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
        }
    }

    @Test
    fun tenDigitsAreSavedInTheSpecsPhoneFormat() {
        // §5.1.1: 000-000-0000.
        saveContactDetails()

        ActivityScenario.launch(SettingsActivity::class.java).use {
            waitForSavedSettings(CollectionMode.TESTING)
            onView(withId(R.id.phoneEditText)).check(matches(withText(PHONE)))

            onView(withId(R.id.settingsEditButton)).perform(click())
            onView(withId(R.id.phoneEditText)).perform(scrollTo(), replaceText("4045551234"), closeSoftKeyboard())
            onView(withId(R.id.submit_edit)).perform(scrollTo(), click())

            onView(withId(R.id.phoneEditText)).check(matches(withText("404-555-1234")))
            eventually { assertEquals("404-555-1234", setting { settings.getPhoneNumber() }) }
            eventually { onView(withId(R.id.phoneEditText)).check(matches(not(isEnabled()))) }
        }
    }

    @Test
    fun invalidPhoneOrEmailIsNotSaved() {
        saveContactDetails()

        ActivityScenario.launch(SettingsActivity::class.java).use {
            waitForSavedSettings(CollectionMode.TESTING)
            onView(withId(R.id.settingsEditButton)).perform(click())
            onView(withId(R.id.phoneEditText)).perform(scrollTo(), replaceText("888-225-532"))
            onView(withId(R.id.emailEditText)).perform(scrollTo(), replaceText("jane.broadband@"), closeSoftKeyboard())
            onView(withId(R.id.submit_edit)).perform(scrollTo(), click())

            onView(withId(R.id.phoneEditText)).check(matches(hasErrorText(context.getString(R.string.invalid_phone))))
            onView(withId(R.id.emailEditText)).check(matches(hasErrorText(context.getString(R.string.invalid_email))))
            onView(withId(R.id.submit_edit)).check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
            assertEquals(PHONE, setting { settings.getPhoneNumber() })
            assertEquals(EMAIL, setting { settings.getEmail() })
        }
    }

    @Test
    fun contactDetailsCantBeBlankedInFccMode() {
        useFccChallengeMode()

        ActivityScenario.launch(SettingsActivity::class.java).use {
            waitForSavedSettings(CollectionMode.FCC_CHALLENGE)
            onView(withId(R.id.settingsEditButton)).perform(click())
            onView(withId(R.id.nameEditText)).perform(scrollTo(), replaceText(""), closeSoftKeyboard())
            onView(withId(R.id.submit_edit)).perform(scrollTo(), click())

            // The save fails in a coroutine, with a toast. Give it time, then check that nothing
            // changed and the details are still open for editing (a save would close them).
            Thread.sleep(1_000)
            onView(withId(R.id.submit_edit)).check(matches(withEffectiveVisibility(Visibility.VISIBLE)))
            assertEquals(NAME, setting { settings.getName() })
        }
    }

    @Test
    fun switchingToFccModeAsksForTheAcknowledgement() {
        saveContactDetails(acknowledged = false)

        ActivityScenario.launch(SettingsActivity::class.java).use {
            waitForSavedSettings(CollectionMode.TESTING)
            chooseCollectionMode(R.string.fcc_challenge_mode)

            onView(withText(R.string.fcc_acknowledgement)).inRoot(isDialog()).check(matches(isDisplayed()))
            onView(withText(R.string.acknowledge)).inRoot(isDialog()).perform(click())

            eventually { assertEquals(CollectionMode.FCC_CHALLENGE, setting { settings.getCollectionMode() }) }
            assertTrue(setting { settings.getFccSharingAcknowledged() })
        }
    }

    @Test
    fun cancellingTheAcknowledgementStaysInTestingMode() {
        saveContactDetails(acknowledged = false)

        ActivityScenario.launch(SettingsActivity::class.java).use {
            waitForSavedSettings(CollectionMode.TESTING)
            chooseCollectionMode(R.string.fcc_challenge_mode)
            onView(withText(R.string.cancel)).inRoot(isDialog()).perform(click())

            onView(withId(R.id.collectionModeTextView)).check(matches(withText(R.string.testing_mode)))
            assertEquals(CollectionMode.TESTING, setting { settings.getCollectionMode() })
            assertFalse(setting { settings.getFccSharingAcknowledged() })
        }
    }

    @Test
    fun fccModeNeedsTheContactDetails() {
        // An FCC challenge needs a name, email and phone (§5.1.1), so the mode goes back to testing.
        setting { settings.setFccSharingAcknowledged(true) }

        ActivityScenario.launch(SettingsActivity::class.java).use {
            waitForSavedSettings(CollectionMode.TESTING)
            chooseCollectionMode(R.string.fcc_challenge_mode)

            eventually { onView(withId(R.id.collectionModeTextView)).check(matches(withText(R.string.testing_mode))) }
            assertEquals(CollectionMode.TESTING, setting { settings.getCollectionMode() })
        }
    }

    @Test
    fun testingModeCanBeChosenAtAnyTime() {
        useFccChallengeMode()

        ActivityScenario.launch(SettingsActivity::class.java).use {
            waitForSavedSettings(CollectionMode.FCC_CHALLENGE)
            chooseCollectionMode(R.string.testing_mode)

            eventually { assertEquals(CollectionMode.TESTING, setting { settings.getCollectionMode() }) }
        }
    }

    // The screen loads the saved settings in a coroutine and sets the collection mode last.
    private fun waitForSavedSettings(mode: CollectionMode) {
        val text = if (mode == CollectionMode.FCC_CHALLENGE) R.string.fcc_challenge_mode else R.string.testing_mode
        eventually { onView(withId(R.id.collectionModeTextView)).check(matches(withText(text))) }
    }

    private fun chooseCollectionMode(@StringRes mode: Int) {
        onView(withId(R.id.collectionModeTextView)).perform(scrollTo(), click())
        onView(withText(mode)).inRoot(isPlatformPopup()).perform(click())
    }
}
