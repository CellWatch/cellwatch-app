package edu.gatech.cc.cellwatch.ui.main

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.net.Uri
import android.widget.PopupMenu
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.Visibility
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import edu.gatech.cc.cellwatch.R
import edu.gatech.cc.cellwatch.data.model.Measurement
import edu.gatech.cc.cellwatch.ui.UiTestSupport.atPosition
import edu.gatech.cc.cellwatch.ui.UiTestSupport.clearHistory
import edu.gatech.cc.cellwatch.ui.UiTestSupport.eventually
import edu.gatech.cc.cellwatch.ui.UiTestSupport.finishAllActivities
import edu.gatech.cc.cellwatch.ui.UiTestSupport.measurement
import edu.gatech.cc.cellwatch.ui.UiTestSupport.resetSettings
import edu.gatech.cc.cellwatch.ui.UiTestSupport.saveTest
import edu.gatech.cc.cellwatch.ui.UiTestSupport.EMAIL
import edu.gatech.cc.cellwatch.ui.UiTestSupport.NAME
import edu.gatech.cc.cellwatch.ui.UiTestSupport.PHONE
import edu.gatech.cc.cellwatch.ui.UiTestSupport.stubActivity
import edu.gatech.cc.cellwatch.ui.UiTestSupport.submission
import edu.gatech.cc.cellwatch.ui.UiTestSupport.useFccChallengeMode
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.hamcrest.Matchers.allOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.time.Duration.Companion.seconds

/**
 * The history screen lists the tests saved on the phone, newest first, and exports the FCC
 * challenge tests.
 */
@RunWith(AndroidJUnit4::class)
class MeasureHistoryActivityTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val earlier = Instant.parse("2024-09-06T21:33:14Z")
    private val later = Instant.parse("2024-09-07T15:02:40Z")

    @Before
    fun emptyHistory() {
        resetSettings()
        clearHistory()
    }

    @After
    fun closeScreens() = finishAllActivities()

    @Test
    fun listsTestsNewestFirstWithWhatKindTheyAre() {
        saveTest(test("testing", earlier))
        saveTest(test("challenge", later), submission("challenge", inVehicle = true))

        ActivityScenario.launch(MeasureHistoryActivity::class.java).use {
            eventually { onView(withId(R.id.recyclerView)).check(matches(atPosition(0, hasDescendant(withText("Challenge"))))) }
            onView(withId(R.id.recyclerView))
                .check(matches(atPosition(0, hasDescendant(withText(R.string.fcc_challenge_vehicle)))))
            onView(withId(R.id.recyclerView)).check(matches(atPosition(1, hasDescendant(withText("Test")))))
            onView(withId(R.id.recyclerView)).check(matches(atPosition(1, hasDescendant(withText(R.string.testing)))))
        }
    }

    @Test
    fun stationaryChallengeIsLabelledStationary() {
        // The submission's in_vehicle_flag (§5.1.2 of the FCC's mobile speed test data spec).
        saveTest(test("challenge", earlier), submission("challenge", inVehicle = false))

        ActivityScenario.launch(MeasureHistoryActivity::class.java).use {
            eventually { onView(withId(R.id.typeText)).check(matches(withText(R.string.fcc_challenge_stationary))) }
        }
    }

    @Test
    fun summaryShowsTheResultsInMillisecondsAndMegabits() {
        // The spec's units: round_trip_time in microseconds (§5.1.6) and bytes_sec in bytes a
        // second (§5.1.4). 30000 µs shows as 30 ms, and 21573627.6 and 2649671.8 bytes a second
        // as 173 and 21 Mbps.
        saveTest(test("g1", earlier))

        ActivityScenario.launch(MeasureHistoryActivity::class.java).use {
            eventually {
                onView(withId(R.id.summaryLatencyText)).check(matches(withText(context.getString(R.string.latency_ms, 30))))
            }
            onView(withId(R.id.summaryDownloadText)).check(matches(withText(context.getString(R.string.speed_mbps, 173))))
            onView(withId(R.id.summaryUploadText)).check(matches(withText(context.getString(R.string.speed_mbps, 21))))
        }
    }

    @Test
    fun failedMeasurementSaysFailed() {
        saveTest(
            listOf(
                measurement("latency", "g1", earlier),
                measurement("download", "g1", earlier + 6.seconds, success = false),
                measurement("upload", "g1", earlier + 14.seconds),
            )
        )

        ActivityScenario.launch(MeasureHistoryActivity::class.java).use {
            eventually { onView(withId(R.id.summaryDownloadText)).check(matches(withText(R.string.failed))) }
            onView(withId(R.id.summaryUploadText)).check(matches(withText(context.getString(R.string.speed_mbps, 21))))
        }
    }

    @Test
    fun expandingATestShowsEachMeasurement() {
        val measurements = test("g1", earlier)
        saveTest(measurements)
        val latency = measurements[0]

        ActivityScenario.launch(MeasureHistoryActivity::class.java).use {
            eventually { onView(withId(R.id.itemHeader)).check(matches(isDisplayed())) }
            onView(withId(R.id.metaContainer)).check(matches(withEffectiveVisibility(Visibility.GONE)))

            onView(withId(R.id.itemHeader)).perform(click())

            onView(withId(R.id.metaContainer)).check(matches(isDisplayed()))
            onView(withId(R.id.carrierText)).check(matches(withText("acme wireless")))
            onView(withId(R.id.uploadedText)).check(matches(withText(R.string.pending)))
            onView(inItem(R.id.latencyItem, R.id.headerText)).check(matches(withText(context.getString(R.string.latency_ms, 30))))
            onView(inItem(R.id.latencyItem, R.id.locationText)).check(matches(withText(centre(latency))))
            // A serving LTE cell with a secondary 5G cell (network_generation, §5.1.8.1).
            onView(inItem(R.id.downloadItem, R.id.technologyText)).check(matches(withText("4G/5G")))
            onView(inItem(R.id.uploadItem, R.id.roamingText)).check(matches(withText(R.string.no)))
        }
    }

    @Test
    fun partsOfATestThatWerentMeasuredAreMarked() {
        // The latency test runs first; this test stopped after it.
        saveTest(listOf(measurement("latency", "g1", earlier)))

        ActivityScenario.launch(MeasureHistoryActivity::class.java).use {
            eventually { onView(withId(R.id.summaryDownloadText)).check(matches(withText("-"))) }
            onView(withId(R.id.summaryUploadText)).check(matches(withText("-")))

            onView(withId(R.id.itemHeader)).perform(click())
            onView(inItem(R.id.downloadItem, R.id.warningText)).check(matches(withText(R.string.not_measured)))
            onView(inItem(R.id.uploadItem, R.id.warningText)).check(matches(withText(R.string.not_measured)))
            onView(inItem(R.id.latencyItem, R.id.warningRow)).check(matches(withEffectiveVisibility(Visibility.GONE)))
        }
    }

    @Test
    fun exportAsksBeforeOpeningTheFilePicker() {
        saveTest(test("challenge", earlier), submission("challenge", inVehicle = false))

        ActivityScenario.launch(MeasureHistoryActivity::class.java).use {
            onView(withId(R.id.export)).perform(click())
            onView(withId(R.id.exportDialogTitle)).inRoot(isDialog()).check(matches(isDisplayed()))

            onView(withText(R.string.cancel_button)).inRoot(isDialog()).perform(click())
            onView(withId(R.id.exportDialogTitle)).check(doesNotExist())
        }
    }

    @Test
    fun exportWritesTheChallengeTestsInTheSpecsFormat() {
        // §5.1 of the FCC's mobile speed test data spec: the submission category, the contact
        // (§5.1.1) and one submission per challenge test. Testing-mode tests aren't exported.
        useFccChallengeMode()
        saveTest(test("challenge", earlier), submission("challenge", inVehicle = false))
        saveTest(test("testing", later))
        val file = File(context.cacheDir, "export.json").apply { delete() }
        val picker = stubFilePicker(Activity.RESULT_OK, Uri.fromFile(file))

        try {
            ActivityScenario.launch(MeasureHistoryActivity::class.java).use {
                export()

                var exported: JsonObject? = null
                eventually { exported = Json.parseToJsonElement(file.readText()).jsonObject }
                val json = exported!!
                assertEquals(setOf("submission_category", "contact", "submissions"), json.keys)
                assertEquals(JsonPrimitive("Consumer Challenge"), json["submission_category"])
                assertEquals(
                    mapOf("name" to JsonPrimitive(NAME), "email" to JsonPrimitive(EMAIL), "phone" to JsonPrimitive(PHONE)),
                    json.getValue("contact").jsonObject,
                )
                val submissions = json.getValue("submissions").jsonArray
                assertEquals(1, submissions.size)
                assertEquals(setOf("download", "upload", "latency"), submissions[0].jsonObject.getValue("tests").jsonObject.keys)
            }
        } finally {
            InstrumentationRegistry.getInstrumentation().removeMonitor(picker)
        }
    }

    @Test
    fun cancellingTheFilePickerWritesNothing() {
        saveTest(test("challenge", earlier), submission("challenge", inVehicle = false))
        val picker = stubFilePicker(Activity.RESULT_CANCELED, uri = null)

        try {
            ActivityScenario.launch(MeasureHistoryActivity::class.java).use {
                export()

                eventually { assertEquals(1, picker.hits) }
                onView(withId(R.id.recyclerView)).check(matches(isDisplayed()))
            }
        } finally {
            InstrumentationRegistry.getInstrumentation().removeMonitor(picker)
        }
    }

    @Test
    fun exportToAFileThatCantBeOpenedLeavesTheHistoryAsItWas() {
        saveTest(test("challenge", earlier), submission("challenge", inVehicle = false))
        val picker = stubFilePicker(Activity.RESULT_OK, Uri.parse("content://edu.gatech.cc.cellwatch.nowhere/export.json"))

        try {
            ActivityScenario.launch(MeasureHistoryActivity::class.java).use {
                export()

                // The phone says the export failed (a toast); the history stays.
                eventually { assertEquals(1, picker.hits) }
                onView(withId(R.id.itemHeader)).check(matches(isDisplayed()))
            }
        } finally {
            InstrumentationRegistry.getInstrumentation().removeMonitor(picker)
        }
    }

    @Test
    fun otherMenuItemsAreLeftToAndroid() {
        ActivityScenario.launch(MeasureHistoryActivity::class.java).use { scenario ->
            var handled = true
            scenario.onActivity { activity ->
                val other = PopupMenu(activity, activity.findViewById(R.id.toolbar)).menu.add("other")
                handled = activity.onOptionsItemSelected(other)
            }

            assertFalse(handled)
        }
    }

    @Test
    fun tappingAnOpenTestClosesIt() {
        saveTest(test("g1", earlier))

        ActivityScenario.launch(MeasureHistoryActivity::class.java).use {
            eventually { onView(withId(R.id.itemHeader)).check(matches(isDisplayed())) }
            onView(withId(R.id.itemHeader)).perform(click())
            onView(withId(R.id.metaContainer)).check(matches(isDisplayed()))

            onView(withId(R.id.itemHeader)).perform(click())

            onView(withId(R.id.metaContainer)).check(matches(withEffectiveVisibility(Visibility.GONE)))
        }
    }

    // Waits for the list, then exports, confirming the dialog.
    private fun export() {
        eventually { onView(withId(R.id.recyclerView)).check(matches(hasDescendant(withId(R.id.itemHeader)))) }
        onView(withId(R.id.export)).perform(click())
        onView(withText(R.string.export_button)).inRoot(isDialog()).perform(click())
    }

    // Android's file picker, answered with [uri] instead of being shown.
    private fun stubFilePicker(resultCode: Int, uri: Uri?) = stubActivity(
        Intent.ACTION_CREATE_DOCUMENT,
        Instrumentation.ActivityResult(resultCode, uri?.let { Intent().setData(it) }),
    )

    // A test's latency, download and upload measurements, in the order they run.
    private fun test(groupId: String, start: Instant) = listOf(
        measurement("latency", groupId, start),
        measurement("download", groupId, start + 6.seconds),
        measurement("upload", groupId, start + 14.seconds),
    )

    private fun inItem(item: Int, view: Int) = allOf(withId(view), isDescendantOfA(withId(item)))

    // The history shows the midpoint of a measurement's first two locations.
    private fun centre(m: Measurement): String {
        val (start, end) = m.locations!!
        return context.getString(R.string.latlon, (start.lat + end.lat) / 2, (start.lon + end.lon) / 2)
    }
}
