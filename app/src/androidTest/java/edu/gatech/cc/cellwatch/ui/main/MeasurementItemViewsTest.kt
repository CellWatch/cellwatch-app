package edu.gatech.cc.cellwatch.ui.main

import android.text.format.DateFormat
import android.view.View
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import edu.gatech.cc.cellwatch.R
import edu.gatech.cc.cellwatch.data.model.FccSubmission
import edu.gatech.cc.cellwatch.data.model.Measurement
import edu.gatech.cc.cellwatch.data.model.MeasurementGroup
import edu.gatech.cc.cellwatch.domain.map.managers.H3Manager
import edu.gatech.cc.cellwatch.domain.telephony.managers.NetworkConnectionType
import edu.gatech.cc.cellwatch.ui.UiTestSupport.context
import edu.gatech.cc.cellwatch.ui.UiTestSupport.finishAllActivities
import edu.gatech.cc.cellwatch.ui.UiTestSupport.measurement
import kotlinx.datetime.Instant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.seconds

/**
 * The views each saved test is shown with, in the history, the map's list and while a test runs,
 * set up here directly with the cases the other UI tests don't reach.
 */
@RunWith(AndroidJUnit4::class)
class MeasurementItemViewsTest {
    private val start = Instant.parse("2024-09-06T21:33:14Z")

    @After
    fun closeScreens() = finishAllActivities()

    @Test
    fun measurementOnWifiSaysWifi() {
        val m = measurement("download", "g1", start).copy(connectionType = NetworkConnectionType.WIFI)

        val item = measurementItem("download", m)

        assertEquals(context.getString(R.string.wifi), item.text(R.id.technologyText))
    }

    @Test
    fun unknownTimeLocationAndRoamingShowADash() {
        val m = measurement("upload", "g1", start).copy(timestamp = null, locations = null, networkRoaming = null)

        val item = measurementItem("upload", m)

        assertEquals("-", item.text(R.id.timeText))
        assertEquals("-", item.text(R.id.locationText))
        assertEquals("-", item.text(R.id.roamingText))
    }

    @Test
    fun roamingSaysYes() {
        val item = measurementItem("latency", measurement("latency", "g1", start).copy(networkRoaming = true))

        assertEquals(context.getString(R.string.yes), item.text(R.id.roamingText))
    }

    @Test
    fun measurementOutsideTheChosenHexagonIsFlagged() {
        // The map's list shows the tests in a hexagon; a test whose locations average out
        // elsewhere is flagged.
        val m = measurement("latency", "g1", start)
        val (lat, lon) = m.centerLatLon()!!
        val itsHexagon = H3Manager.getH3Index(lat, lon, H3Manager.CHILD_HEX_RES)
        val elsewhere = H3Manager.getH3Index(33.7756, -84.3963, H3Manager.CHILD_HEX_RES)

        val outside = measurementItem("latency", m, displayedHexAddress = elsewhere)
        assertTrue(outside.visible(R.id.warningRow))
        assertEquals(context.getString(R.string.not_in_this_hexagon), outside.text(R.id.warningText))

        val inside = measurementItem("latency", m, displayedHexAddress = itsHexagon)
        assertFalse(inside.visible(R.id.warningRow))
    }

    @Test
    fun unknownMeasurementTypeIsAnError() {
        var error: Throwable? = null
        onScreen { activity ->
            error = runCatching { MeasurementItem(activity).setData("jitter", null) }.exceptionOrNull()
        }

        assertTrue(error is RuntimeException)
    }

    @Test
    fun testWithoutALatencyMeasurementTakesTheNextOnesTimeAndCarrier() {
        val download = measurement("download", "g1", start + 6.seconds).copy(provider = "download carrier")
        val upload = measurement("upload", "g1", start + 14.seconds).copy(provider = "upload carrier")

        val withDownload = groupItem(MeasurementGroup(null, download, upload, null))
        assertEquals("download carrier", withDownload.text(R.id.carrierText))
        assertEquals(timeOf(download.timestamp!!), withDownload.text(R.id.timeText))

        val uploadOnly = groupItem(MeasurementGroup(null, null, upload, null))
        assertEquals("upload carrier", uploadOnly.text(R.id.carrierText))
        assertEquals(timeOf(upload.timestamp!!), uploadOnly.text(R.id.timeText))
    }

    @Test
    fun testWithNoMeasurementsHasNoTime() {
        val item = groupItem(MeasurementGroup(null, null, null, FccSubmission(id = "g1")))

        assertEquals("", item.text(R.id.dateText))
        assertEquals("-", item.text(R.id.timeText))
    }

    @Test
    fun uploadTimeIsShownOnceKnown() {
        val uploaded = start + 60.seconds
        var shown: String? = null
        onScreen { activity ->
            val item = MeasurementGroupItem(activity)
            item.updateUploadTime(uploaded)
            shown = item.findViewById<TextView>(R.id.uploadedText).text.toString()
        }

        assertEquals(DateFormat.format("d MMM yyyy h:mm:ss a", uploaded.toEpochMilliseconds()).toString(), shown)
    }

    @Test
    fun menuCanBeBuiltInCode() {
        var built: NavigationDrawer? = null
        onScreen { activity -> built = NavigationDrawer(activity) }

        assertNotNull(built)
    }

    // What a view showed, read on the main thread.
    private class Shown(private val texts: Map<Int, String>, private val visibility: Map<Int, Boolean>) {
        fun text(id: Int) = texts.getValue(id)
        fun visible(id: Int) = visibility.getValue(id)
    }

    private fun measurementItem(type: String, m: Measurement?, displayedHexAddress: Long? = null): Shown {
        lateinit var shown: Shown
        onScreen { activity ->
            val item = MeasurementItem(activity)
            item.setData(type, m, displayedHexAddress)
            shown = read(item, R.id.technologyText, R.id.timeText, R.id.locationText, R.id.roamingText, R.id.warningText, R.id.warningRow)
        }
        return shown
    }

    private fun groupItem(group: MeasurementGroup): Shown {
        lateinit var shown: Shown
        onScreen { activity ->
            val item = MeasurementGroupItem(activity)
            item.setData(group)
            shown = read(item, R.id.carrierText, R.id.dateText, R.id.timeText)
        }
        return shown
    }

    private fun read(view: View, vararg ids: Int) = Shown(
        texts = ids.associateWith { (view.findViewById<View>(it) as? TextView)?.text?.toString() ?: "" },
        visibility = ids.associateWith { view.findViewById<View>(it).isVisible },
    )

    private fun timeOf(instant: Instant) = DateFormat.format("h:mm:ss a", instant.toEpochMilliseconds()).toString()

    private fun onScreen(block: (AboutActivity) -> Unit) {
        ActivityScenario.launch(AboutActivity::class.java).use { scenario -> scenario.onActivity { block(it) } }
    }
}
