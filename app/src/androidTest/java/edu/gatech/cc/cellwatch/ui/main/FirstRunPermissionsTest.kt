package edu.gatech.cc.cellwatch.ui.main

import android.Manifest
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.Visibility
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import edu.gatech.cc.cellwatch.R
import edu.gatech.cc.cellwatch.ui.UiTestSupport.MEASUREMENT_TIMEOUT_MS
import edu.gatech.cc.cellwatch.ui.UiTestSupport.assumeOffline
import edu.gatech.cc.cellwatch.ui.UiTestSupport.clearHistory
import edu.gatech.cc.cellwatch.ui.UiTestSupport.context
import edu.gatech.cc.cellwatch.ui.UiTestSupport.eventually
import edu.gatech.cc.cellwatch.ui.UiTestSupport.finishAllActivities
import edu.gatech.cc.cellwatch.ui.UiTestSupport.foregroundPackage
import edu.gatech.cc.cellwatch.ui.UiTestSupport.grantLocationOnly
import edu.gatech.cc.cellwatch.ui.UiTestSupport.hasPermission
import edu.gatech.cc.cellwatch.ui.UiTestSupport.pressSystemBack
import edu.gatech.cc.cellwatch.ui.UiTestSupport.resetSettings
import edu.gatech.cc.cellwatch.ui.UiTestSupport.setting
import edu.gatech.cc.cellwatch.ui.UiTestSupport.settings
import org.hamcrest.Matchers.anyOf
import org.hamcrest.Matchers.startsWith
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.fail
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

/**
 * What the app does before it has its permissions. Android can't take a granted permission back
 * from a running app, so these only run on a fresh install, before another test has granted
 * them, and skip otherwise. Run them first and on their own:
 *
 *     adb shell am instrument -w -e class edu.gatech.cc.cellwatch.ui.main.FirstRunPermissionsTest \
 *         edu.gatech.cc.cellwatch.test/androidx.test.runner.AndroidJUnitRunner
 *
 * The last one grants location, so they run in name order.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class FirstRunPermissionsTest {
    @Before
    fun firstRun() {
        resetSettings()
        clearHistory()
    }

    @After
    fun cleanUp() {
        finishAllActivities()
        clearHistory()
    }

    @Test
    fun a_goWithoutLocationOffersTheAppsSettings() {
        assumeFalse("location is already granted", hasLocation())

        ActivityScenario.launch(MeasureActivity::class.java).use {
            eventually { onView(withId(R.id.testingWarning)).check(matches(withEffectiveVisibility(Visibility.VISIBLE))) }
            onView(withId(R.id.go)).perform(scrollTo(), click())

            onView(withText(R.string.fgs_location_permission_rationale)).inRoot(isDialog()).check(matches(isDisplayed()))
            onView(withText(R.string.open_settings)).inRoot(isDialog()).perform(click())
            leaveTheOtherApp()
            eventually { onView(withId(R.id.go)).check(matches(withEffectiveVisibility(Visibility.VISIBLE))) }
        }
    }

    @Test
    fun b_readyAsksForThePermissionsAndWaitsIfTheyreRefused() {
        assumeFalse("location or phone state is already granted", hasLocation() || hasPermission(Manifest.permission.READ_PHONE_STATE))

        ActivityScenario.launch(OnboardingActivity::class.java).use {
            repeat(2) { onView(withId(R.id.forward_arrow)).perform(click()) }
            onView(withId(R.id.flTestingMode)).perform(click())
            onView(withId(R.id.forward_arrow)).perform(click())

            onView(withId(R.id.btnReady)).perform(click())
            // Android asks; going Back refuses, and the app waits on this screen.
            leaveTheOtherApp()
            eventually { onView(withId(R.id.btnReady)).check(matches(isDisplayed())) }
            assertFalse(setting { settings.getOnboardingComplete() })
        }
    }

    @Test
    fun c_testThatCantReadThePhoneStateStopsWithAnError() {
        assumeOffline()
        assumeFalse("phone state is already granted", hasPermission(Manifest.permission.READ_PHONE_STATE))
        grantLocationOnly()

        ActivityScenario.launch(MeasureActivity::class.java).use {
            eventually { onView(withId(R.id.testingWarning)).check(matches(withEffectiveVisibility(Visibility.VISIBLE))) }
            onView(withId(R.id.go)).perform(scrollTo(), click())

            // Watching the cells needs phone state, so the latency test throws and the test stops.
            val failed = context.getString(R.string.measurement_failed)
            eventually(MEASUREMENT_TIMEOUT_MS) { onView(withId(R.id.header)).check(matches(withText(startsWith(failed)))) }
            val stoppedAtLatency = context.getString(R.string.measure_steps_stopped_at, 1, 3, context.getString(R.string.latency))
            onView(withId(R.id.stepText))
                .check(matches(anyOf(withText(stoppedAtLatency), withText(R.string.measure_steps_stopped))))

            onView(withId(R.id.takeAnotherButton)).perform(click())
            eventually { onView(withId(R.id.go)).check(matches(withEffectiveVisibility(Visibility.VISIBLE))) }
        }
    }

    private fun hasLocation() =
        hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) || hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)

    // Waits for another app's window (Android's settings or permission dialog), then goes Back,
    // giving the app time to come to the front before going Back again.
    private fun leaveTheOtherApp() {
        eventually(10_000) { assertNotEquals(context.packageName, foregroundPackage()) }
        repeat(3) {
            pressSystemBack()
            val inFront = runCatching { eventually(5_000) { assertEquals(context.packageName, foregroundPackage()) } }
            if (inFront.isSuccess) return
        }
        fail("still in ${foregroundPackage()}")
    }
}
