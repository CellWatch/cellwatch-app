package edu.gatech.cc.cellwatch.ui.main

import androidx.test.ext.junit.runners.AndroidJUnit4
import edu.gatech.cc.cellwatch.data.model.Location
import edu.gatech.cc.cellwatch.domain.map.managers.H3Manager
import edu.gatech.cc.cellwatch.ui.UiTestSupport.clearHistory
import edu.gatech.cc.cellwatch.ui.UiTestSupport.measurement
import edu.gatech.cc.cellwatch.ui.UiTestSupport.saveTest
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Duration.Companion.seconds

/** What the map knows about the saved tests: which hexagons they fall in. */
@RunWith(AndroidJUnit4::class)
class MapViewModelTest {
    private val start = Instant.parse("2024-09-06T21:33:14Z")

    @Before
    fun emptyHistory() = clearHistory()

    @After
    fun cleanUp() = clearHistory()

    @Test
    fun testsAreFiledUnderTheHexagonsTheirLocationsAreIn() {
        saveTest(test("g1"))
        val model = MapViewModel()

        assertEquals(setOf("g1"), runBlocking { model.refreshMeasurementGroups() })

        val group = model.getMeasurementGroups(listOf("g1")).single()
        val (lat, lon) = group.latency!!.centerLatLon()!!
        val small = H3Manager.getH3Index(lat, lon, H3Manager.CHILD_HEX_RES)
        val large = H3Manager.getH3Index(lat, lon, H3Manager.PARENT_HEX_RES)
        assertEquals(setOf("g1"), model.getHexMeasurementGroupIds(small).toSet())
        assertEquals(setOf("g1"), model.getHexMeasurementGroupIds(large).toSet())
        assertEquals(emptySet<String>(), model.getHexMeasurementGroupIds(H3Manager.getH3Index(33.7756, -84.3963, H3Manager.CHILD_HEX_RES)).toSet())
    }

    @Test
    fun aTestNearAnEdgeIsCountedInTheLargeHexagonItIsIn() {
        // H3's small hexagons don't nest exactly in the large ones: near an edge, a location's small
        // hexagon can belong to the next large hexagon over.
        val (lat, lon) = locationWhoseSmallHexagonBelongsToAnotherLargeOne()
        saveTest(test("g1").map { it.copy(locations = listOf(Location(timestamp = start, lat = lat, lon = lon))) })
        val model = MapViewModel()
        runBlocking { model.refreshMeasurementGroups() }

        val small = H3Manager.getH3Index(lat, lon, H3Manager.CHILD_HEX_RES)
        val large = H3Manager.getH3Index(lat, lon, H3Manager.PARENT_HEX_RES)
        val smallsParent = H3Manager.getRelatedH3Hex(small, H3Manager.PARENT_HEX_RES).single()
        assertEquals(setOf("g1"), model.getHexMeasurementGroupIds(small).toSet())
        assertEquals(setOf("g1"), model.getHexMeasurementGroupIds(large).toSet())
        assertEquals(emptySet<String>(), model.getHexMeasurementGroupIds(smallsParent).toSet())
    }

    @Test
    fun refreshingAgainKeepsTheTestsItHas() {
        saveTest(test("g1"))
        val model = MapViewModel()
        runBlocking { model.refreshMeasurementGroups() }
        saveTest(test("g2"))

        assertEquals(setOf("g1", "g2"), runBlocking { model.refreshMeasurementGroups() })
    }

    @Test
    fun aTestTheMapDoesntHaveIsAnError() {
        assertThrows(RuntimeException::class.java) { MapViewModel().getMeasurementGroups(listOf("missing")) }
    }

    @Test
    fun hexagonsOfOtherSizesThanTheMapsAreRefused() {
        val tooSmall = H3Manager.getH3Index(38.8860976, -76.9962846, H3Manager.CHILD_HEX_RES + 1)
        val tooLarge = H3Manager.getH3Index(38.8860976, -76.9962846, H3Manager.PARENT_HEX_RES - 1)

        assertThrows(RuntimeException::class.java) { MapViewModel().getHexMeasurementGroupIds(tooSmall) }
        assertThrows(RuntimeException::class.java) { MapViewModel().getHexMeasurementGroupIds(tooLarge) }
    }

    private fun test(groupId: String) = listOf(
        measurement("latency", groupId, start),
        measurement("download", groupId, start + 6.seconds),
        measurement("upload", groupId, start + 14.seconds),
    )

    // Steps east from the usual test location until the location's small hexagon belongs to another
    // large hexagon than the one the location is in. Small hexagons are about 0.35 km across.
    private fun locationWhoseSmallHexagonBelongsToAnotherLargeOne(): Pair<Double, Double> {
        val lat = 38.8860976
        for (step in 0 until 2_000) {
            val lon = -76.9962846 + step * 0.0002
            val small = H3Manager.getH3Index(lat, lon, H3Manager.CHILD_HEX_RES)
            val large = H3Manager.getH3Index(lat, lon, H3Manager.PARENT_HEX_RES)
            if (H3Manager.getRelatedH3Hex(small, H3Manager.PARENT_HEX_RES).single() != large) {
                return Pair(lat, lon)
            }
        }
        throw AssertionError("no location within 35 km east has a small hexagon outside its large one")
    }
}
