package edu.gatech.cc.cellwatch.ui.main

import android.widget.ImageButton
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.Visibility
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withParent
import androidx.test.ext.junit.runners.AndroidJUnit4
import edu.gatech.cc.cellwatch.R
import edu.gatech.cc.cellwatch.ui.UiTestSupport.clearHistory
import edu.gatech.cc.cellwatch.ui.UiTestSupport.eventually
import edu.gatech.cc.cellwatch.ui.UiTestSupport.finishAllActivities
import edu.gatech.cc.cellwatch.ui.UiTestSupport.finishOnboarding
import edu.gatech.cc.cellwatch.ui.UiTestSupport.openActivities
import edu.gatech.cc.cellwatch.ui.UiTestSupport.resetSettings
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.not
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The side menu that History, Settings, Help and Measure open from their toolbar. The Map item
 * isn't tried: the map screen needs Mapbox, which these offline runs can't reach.
 */
@RunWith(AndroidJUnit4::class)
class NavigationDrawerTest {
    @Before
    fun firstRun() {
        resetSettings()
        clearHistory()
    }

    @After
    fun closeScreens() = finishAllActivities()

    @Test
    fun historyMenuOpensSettings() {
        ActivityScenario.launch(MeasureHistoryActivity::class.java).use {
            openMenu()
            onView(withId(R.id.settingsButton)).perform(click())

            eventually { onView(withId(R.id.settingsEditButton)).check(matches(isDisplayed())) }
        }
    }

    @Test
    fun settingsMenuOpensHistory() {
        ActivityScenario.launch(SettingsActivity::class.java).use {
            openMenu()
            onView(withId(R.id.historyButton)).perform(click())

            // The history is empty, so check the list is there rather than on screen.
            eventually { onView(withId(R.id.recyclerView)).check(matches(withEffectiveVisibility(Visibility.VISIBLE))) }
        }
    }

    @Test
    fun settingsMenuOpensHelp() {
        ActivityScenario.launch(SettingsActivity::class.java).use {
            openMenu()
            onView(withId(R.id.helpButton)).perform(click())

            eventually { onView(withId(R.id.show_dep_licenses)).check(matches(withEffectiveVisibility(Visibility.VISIBLE))) }
        }
    }

    @Test
    fun historyMenuOpensMeasure() {
        ActivityScenario.launch(MeasureHistoryActivity::class.java).use {
            openMenu()
            onView(withId(R.id.menuMeasureButton)).perform(click())

            eventually { onView(withId(R.id.go)).check(matches(withEffectiveVisibility(Visibility.VISIBLE))) }
        }
    }

    @Test
    fun closeButtonClosesTheMenu() {
        ActivityScenario.launch(SettingsActivity::class.java).use {
            openMenu()
            onView(withId(R.id.menuCloseButton)).perform(click())

            eventually { onView(withId(R.id.menuCloseButton)).check(matches(not(isDisplayed()))) }
            onView(withId(R.id.settingsEditButton)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun mapItemOpensTheMap() {
        finishOnboarding()

        ActivityScenario.launch(SettingsActivity::class.java).use {
            openMenu()
            onView(withId(R.id.mapButton)).perform(click())

            eventually { assertTrue(openActivities(MapActivity::class.java).isNotEmpty()) }
        }
    }

    @Test
    fun currentScreensItemOnlyClosesTheMenu() {
        ActivityScenario.launch(SettingsActivity::class.java).use {
            openMenu()
            onView(withId(R.id.settingsButton)).perform(click())

            eventually { onView(withId(R.id.menuCloseButton)).check(matches(not(isDisplayed()))) }
            assertEquals(1, openActivities(SettingsActivity::class.java).size)
        }
    }

    // The toolbar's navigation button, the menu icon, opens the drawer.
    private fun openMenu() {
        onView(allOf(isAssignableFrom(ImageButton::class.java), withParent(withId(R.id.toolbar)))).perform(click())
        eventually { onView(withId(R.id.menuCloseButton)).check(matches(isDisplayed())) }
    }
}
