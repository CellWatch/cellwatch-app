package edu.gatech.cc.cellwatch.ui.main

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.Visibility
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import edu.gatech.cc.cellwatch.R
import edu.gatech.cc.cellwatch.ui.UiTestSupport.eventually
import edu.gatech.cc.cellwatch.ui.UiTestSupport.finishAllActivities
import edu.gatech.cc.cellwatch.ui.UiTestSupport.resetSettings
import edu.gatech.cc.cellwatch.ui.UiTestSupport.useFccChallengeMode
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The screen before a test starts. These don't press Go, which would start a real measurement.
 */
@RunWith(AndroidJUnit4::class)
class PreMeasureTest {
    @Before
    fun firstRun() = resetSettings()

    @After
    fun closeScreens() = finishAllActivities()

    @Test
    fun testingModeWarnsThatTheTestWontGoToTheFcc() {
        ActivityScenario.launch(MeasureActivity::class.java).use {
            // The screen reads the collection mode in a coroutine.
            eventually {
                visibility(R.id.testingWarning, Visibility.VISIBLE)
                visibility(R.id.stationaryOrMoving, Visibility.GONE)
                visibility(R.id.certifications, Visibility.GONE)
            }
            visibility(R.id.go, Visibility.VISIBLE)
        }
    }

    @Test
    fun fccModeAsksWhetherTheTestIsStationaryOrMoving() {
        // in_vehicle_flag in §5.1.2 of the FCC's mobile speed test data spec (July 28, 2025).
        useFccChallengeMode()

        ActivityScenario.launch(MeasureActivity::class.java).use {
            waitForFccMode()
            visibility(R.id.certifications, Visibility.VISIBLE)
            // Stationary until chosen otherwise.
            visibility(R.id.stationaryCheck, Visibility.VISIBLE)
            visibility(R.id.movingCheck, Visibility.GONE)
        }
    }

    @Test
    fun choosingMovingMarksTheTestInVehicle() {
        useFccChallengeMode()

        ActivityScenario.launch(MeasureActivity::class.java).use {
            waitForFccMode()

            onView(withId(R.id.moving)).perform(scrollTo(), click())
            eventually { visibility(R.id.movingCheck, Visibility.VISIBLE) }
            visibility(R.id.stationaryCheck, Visibility.GONE)

            onView(withId(R.id.stationary)).perform(scrollTo(), click())
            eventually { visibility(R.id.stationaryCheck, Visibility.VISIBLE) }
            visibility(R.id.movingCheck, Visibility.GONE)
        }
    }

    private fun waitForFccMode() = eventually {
        visibility(R.id.stationaryOrMoving, Visibility.VISIBLE)
        visibility(R.id.testingWarning, Visibility.GONE)
    }

    private fun visibility(id: Int, visibility: Visibility) {
        onView(withId(id)).check(matches(withEffectiveVisibility(visibility)))
    }
}
