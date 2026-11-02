package edu.gatech.cc.cellwatch.ui.main

import android.location.Location
import android.view.View
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.Visibility
import androidx.test.espresso.matcher.ViewMatchers.isChecked
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.mapbox.geojson.Point
import com.mapbox.maps.MapView
import edu.gatech.cc.cellwatch.R
import edu.gatech.cc.cellwatch.domain.fcc.grantMeasurementPermissions
import edu.gatech.cc.cellwatch.ui.UiTestSupport.clearHistory
import edu.gatech.cc.cellwatch.ui.UiTestSupport.eventually
import edu.gatech.cc.cellwatch.ui.UiTestSupport.finishAllActivities
import edu.gatech.cc.cellwatch.ui.UiTestSupport.finishOnboarding
import edu.gatech.cc.cellwatch.ui.UiTestSupport.measurement
import edu.gatech.cc.cellwatch.ui.UiTestSupport.phoneLocation
import edu.gatech.cc.cellwatch.ui.UiTestSupport.resetSettings
import edu.gatech.cc.cellwatch.ui.UiTestSupport.saveTest
import kotlinx.datetime.Instant
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.not
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.seconds
import edu.gatech.cc.cellwatch.data.model.Location as TestLocation

/**
 * The map screen, as far as it works without a network. Mapbox can't load the map's style offline,
 * so what waits for the style isn't tried: the location puck, following the camera, and tapping
 * hexagons or pins to list their tests.
 */
@RunWith(AndroidJUnit4::class)
class MapActivityTest {
    private val start = Instant.parse("2024-09-06T21:33:14Z")

    @Before
    fun setUp() {
        resetSettings()
        clearHistory()
        grantMeasurementPermissions()
    }

    @After
    fun cleanUp() {
        finishAllActivities()
        clearHistory()
    }

    @Test
    fun beforeOnboardingTheMapSendsYouThere() {
        val scenario = ActivityScenario.launch(MapActivity::class.java)

        eventually { onView(withId(R.id.button_readmore)).check(matches(isDisplayed())) }
        eventually { assertEquals(Lifecycle.State.DESTROYED, scenario.state) }
        scenario.close()
    }

    @Test
    fun measureButtonOpensTheMeasureScreen() {
        finishOnboarding()

        ActivityScenario.launch(MapActivity::class.java).use {
            onView(withId(R.id.measureButton)).perform(click())

            eventually { onView(withId(R.id.go)).check(matches(withEffectiveVisibility(Visibility.VISIBLE))) }
        }
    }

    @Test
    fun menuButtonOpensAndClosesTheMenu() {
        finishOnboarding()

        ActivityScenario.launch(MapActivity::class.java).use { scenario ->
            onView(withId(R.id.sideMenuButton)).perform(click())
            eventually { onView(withId(R.id.menuCloseButton)).check(matches(isDisplayed())) }

            // The open menu covers the button, so press it in code.
            scenario.onActivity { it.findViewById<View>(R.id.sideMenuButton).performClick() }
            eventually { onView(withId(R.id.menuCloseButton)).check(matches(not(isDisplayed()))) }
        }
    }

    @Test
    fun centreButtonMovesTheMapToThePhone() {
        finishOnboarding()
        val phone = phoneLocation()

        ActivityScenario.launch(MapActivity::class.java).use { scenario ->
            onView(withId(R.id.centerUserButton)).perform(click())

            eventually(10_000) { assertNear(phone, mapCentre(scenario)) }
        }
    }

    @Test
    fun searchingOfflineLeavesTheMapUsable() {
        finishOnboarding()

        ActivityScenario.launch(MapActivity::class.java).use {
            onView(withId(R.id.query_edit_text)).perform(click(), replaceText("Atlanta"))
            onView(withId(R.id.clearButton)).perform(click())

            onView(withId(R.id.query_edit_text)).check(matches(withText("")))
            onView(withId(R.id.search_results_view)).check(matches(withEffectiveVisibility(Visibility.GONE)))

            // Back hides search results rather than leaving the map.
            Espresso.closeSoftKeyboard()
            Espresso.pressBack()
            onView(withId(R.id.measureButton)).check(matches(isDisplayed()))
        }
    }

    @Test
    fun hexagonSwitchSwapsHexagonsForPins() {
        finishOnboarding()
        val phone = phoneLocation()
        // A test where the phone is, and one with no location, which can't go on the map.
        saveTest(testAt("here", phone.latitude, phone.longitude))
        saveTest(listOf(measurement("latency", "nowhere", start).copy(locations = emptyList())))

        ActivityScenario.launch(MapActivity::class.java).use { scenario ->
            onView(withId(R.id.centerUserButton)).perform(click())
            eventually(10_000) { assertNear(phone, mapCentre(scenario)) }
            onView(withId(R.id.h3ToggleSwitch)).check(matches(isChecked()))

            // Pins instead, and a tap on the map, which looks for a cluster of pins there.
            onView(withId(R.id.h3ToggleSwitch)).perform(click())
            // Mapbox's MapView holds a SurfaceView with the same ID.
            onView(allOf(withId(R.id.mapView), isAssignableFrom(MapView::class.java))).perform(click())

            // Hexagons again, now around the phone: the test's hexagon shows a count of 1.
            onView(withId(R.id.h3ToggleSwitch)).perform(click())
            eventually(10_000) { assertTrue(hexagonCounts(scenario).contains("1")) }

            // Off and on again redraws the hexagons already shown.
            onView(withId(R.id.h3ToggleSwitch)).perform(click())
            onView(withId(R.id.h3ToggleSwitch)).perform(click())
            eventually(10_000) { assertTrue(hexagonCounts(scenario).contains("1")) }
        }
    }

    @Test
    fun listSheetShowsItsHeaderOnlyWhileOpen() {
        finishOnboarding()

        ActivityScenario.launch(MapActivity::class.java).use { scenario ->
            // Tapping a hexagon or pin opens the sheet, which needs the map's style, so open it in code.
            scenario.onActivity { activity ->
                ViewModelProvider(activity)[MapViewModel::class.java].selectedMeasurementGroupIds = setOf("g1")
                BottomSheetBehavior.from(activity.findViewById<View>(R.id.sheet)).state = BottomSheetBehavior.STATE_EXPANDED
            }
            eventually { onView(withId(R.id.sheetHeader)).check(matches(isDisplayed())) }
            onView(withId(R.id.sheetHandle)).check(matches(withEffectiveVisibility(Visibility.GONE)))

            onView(withId(R.id.sheetCloseBtn)).perform(click())

            eventually { onView(withId(R.id.sheetHeader)).check(matches(withEffectiveVisibility(Visibility.GONE))) }
            var selected = setOf("not read")
            scenario.onActivity { selected = ViewModelProvider(it)[MapViewModel::class.java].selectedMeasurementGroupIds }
            assertEquals(emptySet<String>(), selected)
        }
    }

    private fun testAt(groupId: String, lat: Double, lon: Double) = listOf("latency", "download", "upload").map {
        measurement(it, groupId, start).copy(
            locations = listOf(TestLocation(timestamp = start, lat = lat, lon = lon), TestLocation(timestamp = start + 5.seconds, lat = lat, lon = lon)),
        )
    }

    private fun mapCentre(scenario: ActivityScenario<MapActivity>): Point {
        lateinit var centre: Point
        scenario.onActivity { centre = it.findViewById<MapView>(R.id.mapView).mapboxMap.cameraState.center }
        return centre
    }

    private fun hexagonCounts(scenario: ActivityScenario<MapActivity>): List<String> {
        var counts = listOf<String>()
        scenario.onActivity { activity ->
            counts = activity.findViewById<MapView>(R.id.mapView).viewAnnotationManager.annotations.keys
                .mapNotNull { it.findViewById<TextView>(R.id.textView_measurements)?.text?.toString() }
        }
        return counts
    }

    private fun assertNear(phone: Location, centre: Point) {
        assertEquals(phone.latitude, centre.latitude(), 0.001)
        assertEquals(phone.longitude, centre.longitude(), 0.001)
    }
}
