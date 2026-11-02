package edu.gatech.cc.cellwatch.ui.main

import android.app.ActivityManager
import android.content.ComponentName
import android.view.View
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
import edu.gatech.cc.cellwatch.domain.fcc.MeasurementService
import edu.gatech.cc.cellwatch.domain.fcc.grantMeasurementPermissions
import edu.gatech.cc.cellwatch.ui.UiTestSupport.LocationHold
import edu.gatech.cc.cellwatch.ui.UiTestSupport.MEASUREMENT_TIMEOUT_MS
import edu.gatech.cc.cellwatch.ui.UiTestSupport.assumeOffline
import edu.gatech.cc.cellwatch.ui.UiTestSupport.clearHistory
import edu.gatech.cc.cellwatch.ui.UiTestSupport.context
import edu.gatech.cc.cellwatch.ui.UiTestSupport.eventually
import edu.gatech.cc.cellwatch.ui.UiTestSupport.finishAllActivities
import edu.gatech.cc.cellwatch.ui.UiTestSupport.openActivities
import edu.gatech.cc.cellwatch.ui.UiTestSupport.phoneLocation
import edu.gatech.cc.cellwatch.ui.UiTestSupport.resetSettings
import edu.gatech.cc.cellwatch.ui.UiTestSupport.shell
import edu.gatech.cc.cellwatch.ui.UiTestSupport.useFccChallengeMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs real measurements from the Measure screen, which only happens with no network (see
 * [assumeOffline]). With none, the server lookup fails at once and each part of the test is saved
 * as failed, which still takes the screen through every step.
 *
 * None of them makes an FCC submission: the one in FCC mode runs with mobile data off, which keeps
 * a test from the FCC, and the rest are in testing mode. What they save is cleared afterwards.
 */
@RunWith(AndroidJUnit4::class)
class MeasureFlowTest {
    private var mobileDataTurnedOff = false

    @Before
    fun offlineWithPermissions() {
        assumeOffline()
        grantMeasurementPermissions()
        // Fused location only finds a fix for an app with a screen showing. It doesn't hand back
        // the mock one a LocationHold may have left.
        ActivityScenario.launch(MeasureActivity::class.java).use { phoneLocation() }
        resetSettings()
        clearHistory()
    }

    @After
    fun cleanUp() {
        finishAllActivities()
        if (mobileDataTurnedOff) shell("svc data enable")
        clearHistory()
    }

    @Test
    fun testingModeTestRunsThroughEveryStep() {
        ActivityScenario.launch(MeasureActivity::class.java).use {
            eventually { onView(withId(R.id.testingWarning)).check(matches(withEffectiveVisibility(Visibility.VISIBLE))) }
            onView(withId(R.id.go)).perform(scrollTo(), click())

            eventually(MEASUREMENT_TIMEOUT_MS) {
                onView(withId(R.id.header)).check(matches(withText(R.string.measurement_complete)))
            }
            // Offline, every part fails.
            onView(withId(R.id.stepText)).check(matches(withText(R.string.measure_steps_some_failed)))
            onView(withId(R.id.summaryLatencyText)).check(matches(withText(R.string.failed)))
            onView(withId(R.id.buttonContainer)).check(matches(isDisplayed()))

            onView(withId(R.id.takeAnotherButton)).perform(click())
            eventually { onView(withId(R.id.go)).check(matches(withEffectiveVisibility(Visibility.VISIBLE))) }
        }
    }

    @Test
    fun withMobileDataOffFccModeAsksBeforeMeasuring() {
        useFccChallengeMode()
        shell("svc data disable")
        mobileDataTurnedOff = true

        ActivityScenario.launch(MeasureActivity::class.java).use {
            eventually { onView(withId(R.id.stationaryOrMoving)).check(matches(withEffectiveVisibility(Visibility.VISIBLE))) }

            // Cancelling leaves the screen as it was.
            onView(withId(R.id.go)).perform(scrollTo(), click())
            onView(withText(R.string.not_cellular_warning)).inRoot(isDialog()).check(matches(isDisplayed()))
            onView(withText(R.string.cancel)).inRoot(isDialog()).perform(click())
            onView(withId(R.id.go)).check(matches(withEffectiveVisibility(Visibility.VISIBLE)))

            onView(withId(R.id.go)).perform(scrollTo(), click())
            onView(withText(R.string.not_cellular_proceed)).inRoot(isDialog()).perform(click())
            eventually(MEASUREMENT_TIMEOUT_MS) {
                onView(withId(R.id.header)).check(matches(withText(R.string.measurement_complete)))
            }

            onView(withId(R.id.backToMapButton)).perform(click())
            eventually { assertTrue(openActivities(MapActivity::class.java).isNotEmpty()) }
        }
    }

    @Test
    fun aSecondQuickTapOnGoDoesntStartTheServiceAgain() {
        LocationHold().use { location ->
            ActivityScenario.launch(MeasureActivity::class.java).use { scenario ->
                eventually { onView(withId(R.id.testingWarning)).check(matches(withEffectiveVisibility(Visibility.VISIBLE))) }
                onView(withId(R.id.go)).perform(scrollTo())
                // Both taps arrive before the first one's start reaches the service.
                scenario.onActivity { activity -> activity.findViewById<View>(R.id.go).run { performClick(); performClick() } }

                // Once the test's screen replaces Go's, neither tap can start anything more. The
                // test is held at its first location fix, so its service is still there to ask.
                eventually { onView(withId(R.id.header)).check(matches(isDisplayed())) }
                assertEquals("start commands", 1, measurementServiceStarts())
                assertStartedInTheForeground()

                location.release()
                eventually(MEASUREMENT_TIMEOUT_MS) {
                    onView(withId(R.id.header)).check(matches(withText(R.string.measurement_complete)))
                }
            }
        }
    }

    /** How many start commands the measurement service has had, from Android's record of it. */
    private fun measurementServiceStarts(): Int {
        val service = ComponentName(context, MeasurementService::class.java).flattenToShortString()
        // The dump can begin with another app's service, the last one that didn't answer in time.
        val record = shell("dumpsys activity services $service").split("ServiceRecord{").drop(1)
            .firstOrNull { it.substringBefore('\n').contains(service) }
        return record?.let { Regex("""lastStartId=(\d+)""").find(it) }?.groupValues?.get(1)?.toInt() ?: 0
    }

    @Suppress("DEPRECATION") // getRunningServices still lists the app's own services
    private fun assertStartedInTheForeground() {
        val service = context.getSystemService(ActivityManager::class.java).getRunningServices(Int.MAX_VALUE)
            .firstOrNull { it.service == ComponentName(context, MeasurementService::class.java) }
        assertNotNull("measurement service isn't running", service)
        assertTrue("measurement service isn't started", service!!.started)
        assertTrue("measurement service isn't in the foreground", service.foreground)
    }
}
