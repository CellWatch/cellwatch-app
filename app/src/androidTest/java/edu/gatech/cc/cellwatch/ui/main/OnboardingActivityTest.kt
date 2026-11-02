package edu.gatech.cc.cellwatch.ui.main

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.Visibility
import androidx.test.espresso.matcher.ViewMatchers.hasErrorText
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import edu.gatech.cc.cellwatch.R
import edu.gatech.cc.cellwatch.data.model.CollectionMode
import edu.gatech.cc.cellwatch.domain.fcc.grantMeasurementPermissions
import edu.gatech.cc.cellwatch.ui.UiTestSupport.EMAIL
import edu.gatech.cc.cellwatch.ui.UiTestSupport.NAME
import edu.gatech.cc.cellwatch.ui.UiTestSupport.PHONE
import edu.gatech.cc.cellwatch.ui.UiTestSupport.eventually
import edu.gatech.cc.cellwatch.ui.UiTestSupport.finishAllActivities
import edu.gatech.cc.cellwatch.ui.UiTestSupport.resetSettings
import edu.gatech.cc.cellwatch.ui.UiTestSupport.setting
import edu.gatech.cc.cellwatch.ui.UiTestSupport.settings
import edu.gatech.cc.cellwatch.ui.UiTestSupport.useFccChallengeMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The first-run screens: Welcome, Data Use, Collection Mode, the contact details the FCC needs
 * (FCC Challenge mode only) and Permissions.
 */
@RunWith(AndroidJUnit4::class)
class OnboardingActivityTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun firstRun() = resetSettings()

    @After
    fun closeScreens() = finishAllActivities()

    @Test
    fun welcomeScreenHasNoBackArrow() {
        ActivityScenario.launch(OnboardingActivity::class.java).use {
            onView(withId(R.id.button_readmore)).check(matches(isDisplayed()))
            onView(withId(R.id.back_arrow)).check(matches(withEffectiveVisibility(Visibility.INVISIBLE)))
            onView(withId(R.id.forward_arrow)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun readMoreHidesTheNavigationUntilItsClosed() {
        ActivityScenario.launch(OnboardingActivity::class.java).use {
            onView(withId(R.id.button_readmore)).perform(click())
            onView(withId(R.id.buttonExitTop)).check(matches(isDisplayed()))
            onView(withId(R.id.nav)).check(matches(withEffectiveVisibility(Visibility.GONE)))

            onView(withId(R.id.buttonExitTop)).perform(click())
            onView(withId(R.id.button_readmore)).check(matches(isDisplayed()))
            onView(withId(R.id.nav)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun backArrowReturnsToTheScreenBefore() {
        ActivityScenario.launch(OnboardingActivity::class.java).use {
            next()
            next()
            onView(withId(R.id.flTestingMode)).check(matches(isDisplayed()))

            onView(withId(R.id.back_arrow)).perform(click())
            onView(withId(R.id.back_arrow)).perform(click())
            onView(withId(R.id.button_readmore)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun testingModeSkipsTheContactDetails() {
        useFccChallengeMode()
        runOnboardingAgain()

        ActivityScenario.launch(OnboardingActivity::class.java).use {
            next()
            next()
            onView(withId(R.id.flTestingMode)).perform(click())
            onView(withId(R.id.testingModeCheck)).check(matches(isDisplayed()))
            onView(withId(R.id.fccModeCheck)).check(matches(withEffectiveVisibility(Visibility.GONE)))
            next()

            onView(withId(R.id.btnReady)).check(matches(isDisplayed()))
            onView(withId(R.id.forward_arrow)).check(matches(withEffectiveVisibility(Visibility.INVISIBLE)))
            eventually { assertEquals(CollectionMode.TESTING, setting { settings.getCollectionMode() }) }
        }
    }

    @Test
    fun fccModeNeedsEveryContactDetail() {
        ActivityScenario.launch(OnboardingActivity::class.java).use {
            goToContactDetails()
            next()

            // Still on the contact details, with each field marked.
            onView(withId(R.id.etName)).check(matches(hasErrorText(context.getString(R.string.blank_name))))
            onView(withId(R.id.etPhone)).check(matches(hasErrorText(context.getString(R.string.invalid_phone))))
            onView(withId(R.id.etEmail)).check(matches(hasErrorText(context.getString(R.string.invalid_email))))
            assertEquals(CollectionMode.TESTING, setting { settings.getCollectionMode() })
        }
    }

    @Test
    fun phoneNumberMustBeInTheSpecsFormat() {
        // §5.1.1 of the FCC's mobile speed test data spec: a US phone number, 000-000-0000.
        ActivityScenario.launch(OnboardingActivity::class.java).use {
            goToContactDetails()
            fillContactDetails(phone = "888-225-532")
            next()

            onView(withId(R.id.etPhone)).check(matches(hasErrorText(context.getString(R.string.invalid_phone))))
            onView(withId(R.id.btnReady)).check(doesNotExist())
            assertEquals("", setting { settings.getPhoneNumber() })
        }
    }

    @Test
    fun contactDetailsAreSavedInTheSpecsFormat() {
        // §5.1.1: ten digits are saved as 000-000-0000.
        ActivityScenario.launch(OnboardingActivity::class.java).use {
            goToContactDetails()
            fillContactDetails(phone = PHONE.replace("-", ""))
            next()

            onView(withId(R.id.btnReady)).check(matches(isDisplayed()))
            eventually {
                assertEquals(NAME, setting { settings.getName() })
                assertEquals(PHONE, setting { settings.getPhoneNumber() })
                assertEquals(EMAIL, setting { settings.getEmail() })
                assertTrue(setting { settings.getFccSharingAcknowledged() })
                assertEquals(CollectionMode.FCC_CHALLENGE, setting { settings.getCollectionMode() })
            }
        }
    }

    @Test
    fun readyFinishesOnboardingOnceThePermissionsAreGranted() {
        grantMeasurementPermissions()

        val scenario = ActivityScenario.launch(OnboardingActivity::class.java)
        next()
        next()
        onView(withId(R.id.flTestingMode)).perform(click())
        next()
        onView(withId(R.id.btnReady)).perform(click())

        eventually { assertTrue(setting { settings.getOnboardingComplete() }) }
        eventually { assertEquals(Lifecycle.State.DESTROYED, scenario.state) }
        scenario.close()
    }

    private fun next() {
        onView(withId(R.id.forward_arrow)).perform(click())
    }

    private fun goToContactDetails() {
        next()
        next()
        onView(withId(R.id.fccModeCheck)).check(matches(isDisplayed())) // FCC mode is the default
        next()
        onView(withId(R.id.etName)).check(matches(isDisplayed()))
    }

    private fun fillContactDetails(phone: String) {
        onView(withId(R.id.etName)).perform(scrollTo(), replaceText(NAME))
        onView(withId(R.id.etPhone)).perform(scrollTo(), replaceText(phone))
        onView(withId(R.id.etEmail)).perform(scrollTo(), replaceText(EMAIL), closeSoftKeyboard())
        onView(withId(R.id.cbAcknowledgement)).perform(scrollTo(), click())
    }

    // Onboarding again, with the FCC mode and contact details of an earlier run still saved.
    private fun runOnboardingAgain() = setting { settings.setOnboardingComplete(false) }
}
