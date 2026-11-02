package edu.gatech.cc.cellwatch.ui.main

import android.Manifest
import android.content.pm.PackageManager
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.Visibility
import androidx.test.espresso.matcher.ViewMatchers.hasErrorText
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
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
import edu.gatech.cc.cellwatch.ui.UiTestSupport.leaveTheOtherApp
import edu.gatech.cc.cellwatch.ui.UiTestSupport.resetSettings
import edu.gatech.cc.cellwatch.ui.UiTestSupport.setting
import edu.gatech.cc.cellwatch.ui.UiTestSupport.settings
import edu.gatech.cc.cellwatch.ui.UiTestSupport.useFccChallengeMode
import edu.gatech.cc.cellwatch.ui.onboarding.ReadMoreFragment
import org.hamcrest.Matchers.containsString
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    @Test
    fun swipingMovesBetweenScreens() {
        ActivityScenario.launch(OnboardingActivity::class.java).use {
            onView(withId(R.id.content_frame)).perform(swipe(fromX = 0.3f, toX = -0.3f))
            eventually { onView(withId(R.id.back_arrow)).check(matches(isDisplayed())) } // Data Use

            // Up and down don't change screens.
            onView(withId(R.id.content_frame)).perform(swipe(fromY = 0.3f, toY = -0.3f))
            onView(withId(R.id.back_arrow)).check(matches(isDisplayed()))

            onView(withId(R.id.content_frame)).perform(swipe(fromX = -0.3f, toX = 0.3f))
            eventually { onView(withId(R.id.button_readmore)).check(matches(isDisplayed())) }
        }
    }

    @Test
    fun recreatingTheActivityKeepsTheScreenAndTheChosenMode() {
        ActivityScenario.launch(OnboardingActivity::class.java).use { scenario ->
            next()
            next()
            onView(withId(R.id.flTestingMode)).perform(click())

            scenario.recreate()

            onView(withId(R.id.testingModeCheck)).check(matches(isDisplayed()))
            onView(withId(R.id.fccModeCheck)).check(matches(withEffectiveVisibility(Visibility.GONE)))
            next()
            onView(withId(R.id.btnReady)).check(matches(isDisplayed())) // testing mode skips the contact details
        }
    }

    @Test
    fun screensMatchTheirFragments() {
        assertTrue(OnboardingActivity.Screen.READ_MORE.toNewFragment() is ReadMoreFragment)
        assertNull(OnboardingActivity.Screen.fromFragment(Fragment()))
    }

    @Test
    fun permissionAnswersFromAndroidAreHandled() {
        ActivityScenario.launch(OnboardingActivity::class.java).use { scenario ->
            next()
            next()
            onView(withId(R.id.flTestingMode)).perform(click())
            next()

            // What Android reports once the person answers its permission dialog (FirstRunPermissionsTest
            // shows the dialog itself). Request code 1 is the screen's own request.
            val permissions = arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.READ_PHONE_STATE,
            )
            scenario.onActivity { activity ->
                val screen = activity.supportFragmentManager.findFragmentById(R.id.content_frame)!!
                @Suppress("DEPRECATION")
                screen.onRequestPermissionsResult(2, permissions, IntArray(3) { PackageManager.PERMISSION_GRANTED })
                // An interrupted request comes back empty: no answer at all.
                @Suppress("DEPRECATION")
                screen.onRequestPermissionsResult(1, emptyArray(), IntArray(0))
            }
            assertFalse("another request, or an interrupted one, leaves onboarding open", setting { settings.getOnboardingComplete() })
            onView(withId(R.id.btnReady)).check(matches(isDisplayed())) // and shows no dialog

            scenario.onActivity { activity ->
                val screen = activity.supportFragmentManager.findFragmentById(R.id.content_frame)!!
                @Suppress("DEPRECATION")
                screen.onRequestPermissionsResult(1, permissions, IntArray(3) { PackageManager.PERMISSION_DENIED })
            }
            assertFalse("a refusal leaves onboarding open", setting { settings.getOnboardingComplete() })
            // The refusal says what's missing.
            onView(withText(containsString(context.getString(R.string.permission_location))))
                .inRoot(isDialog()).check(matches(isDisplayed()))
            onView(withText(containsString(context.getString(R.string.permission_phone))))
                .inRoot(isDialog()).check(matches(isDisplayed()))
            onView(withText(R.string.cancel_button)).inRoot(isDialog()).perform(click())
            onView(withId(R.id.btnReady)).check(matches(isDisplayed()))

            scenario.onActivity { activity ->
                val screen = activity.supportFragmentManager.findFragmentById(R.id.content_frame)!!
                @Suppress("DEPRECATION")
                screen.onRequestPermissionsResult(1, permissions, IntArray(3) { PackageManager.PERMISSION_GRANTED })
            }
            eventually { assertTrue(setting { settings.getOnboardingComplete() }) }
            eventually { assertEquals(Lifecycle.State.DESTROYED, scenario.state) }
        }
    }

    @Test
    fun choosingApproximateLocationAsksForPreciseLocation() {
        ActivityScenario.launch(OnboardingActivity::class.java).use { scenario ->
            next()
            next()
            onView(withId(R.id.flTestingMode)).perform(click())
            next()

            // What Android reports when the person allows only approximate location.
            scenario.onActivity { activity ->
                val screen = activity.supportFragmentManager.findFragmentById(R.id.content_frame)!!
                @Suppress("DEPRECATION")
                screen.onRequestPermissionsResult(
                    1,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                    intArrayOf(PackageManager.PERMISSION_DENIED, PackageManager.PERMISSION_GRANTED),
                )
            }
            onView(withText(containsString(context.getString(R.string.permission_precise_location))))
                .inRoot(isDialog()).check(matches(isDisplayed()))
            onView(withText(R.string.cancel_button)).inRoot(isDialog()).perform(click())
            assertFalse(setting { settings.getOnboardingComplete() })
        }
    }

    @Test
    fun comingBackFromSettingsWithThePermissionsFinishesOnboarding() {
        grantMeasurementPermissions()

        val scenario = ActivityScenario.launch(OnboardingActivity::class.java)
        next()
        next()
        onView(withId(R.id.flTestingMode)).perform(click())
        next()

        // A refusal, then the person goes to the app's settings and comes back with everything allowed.
        scenario.onActivity { activity ->
            val screen = activity.supportFragmentManager.findFragmentById(R.id.content_frame)!!
            @Suppress("DEPRECATION")
            screen.onRequestPermissionsResult(
                1,
                arrayOf(Manifest.permission.READ_PHONE_STATE),
                intArrayOf(PackageManager.PERMISSION_DENIED),
            )
        }
        onView(withText(R.string.open_settings)).inRoot(isDialog()).perform(click())
        leaveTheOtherApp()

        eventually { assertTrue(setting { settings.getOnboardingComplete() }) }
        eventually { assertEquals(Lifecycle.State.DESTROYED, scenario.state) }
        scenario.close()
    }

    private fun next() {
        onView(withId(R.id.forward_arrow)).perform(click())
    }

    // A 100 ms fling through the middle of the view, by fractions of its size from its centre.
    // Espresso's swipeLeft and the like start at the screen's edge, where Android takes them as
    // Back or Home, and send the finger's release late enough that Android reads no speed. These
    // events carry their own times, 10 ms apart.
    private fun swipe(fromX: Float = 0f, toX: Float = 0f, fromY: Float = 0f, toY: Float = 0f) = object : ViewAction {
        override fun getConstraints() = isDisplayed()

        override fun getDescription() = "fling from ($fromX, $fromY) to ($toX, $toY) of the view"

        override fun perform(uiController: UiController, view: View) {
            val corner = IntArray(2).also { view.getLocationOnScreen(it) }
            fun x(f: Float) = corner[0] + view.width * (0.5f + f)
            fun y(f: Float) = corner[1] + view.height * (0.5f + f)
            val down = SystemClock.uptimeMillis()
            val events = listOf(MotionEvent.obtain(down, down, MotionEvent.ACTION_DOWN, x(fromX), y(fromY), 0)) +
                (1..10).map { step ->
                    val f = step / 10f
                    MotionEvent.obtain(
                        down, down + step * 10L, MotionEvent.ACTION_MOVE,
                        x(fromX + (toX - fromX) * f), y(fromY + (toY - fromY) * f), 0,
                    )
                } +
                MotionEvent.obtain(down, down + 100L, MotionEvent.ACTION_UP, x(toX), y(toY), 0)
            for (event in events) {
                event.source = InputDevice.SOURCE_TOUCHSCREEN
                uiController.injectMotionEvent(event)
                event.recycle()
            }
            uiController.loopMainThreadUntilIdle()
        }
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
