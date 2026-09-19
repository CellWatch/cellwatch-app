package edu.gatech.cc.cellwatch.domain.maphome

import com.beriukhov.h3.H3
import com.beriukhov.h3.LatLng as H3LatLng
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Proves H3 is usable from Kotlin/Native, which is the whole reason for taking
 * this dependency.
 *
 * H3 is the unit the rest of the system already aggregates by: the published
 * schema stores center_hex9/start_hex9/end_hex9 and selects with
 * hex_ancestor(...). Only two operations need the library - point to cell, and
 * cell to boundary - because parent/child derivation is bit masking.
 *
 * iOS-only because h3-kmp publishes android and ios variants, not jvm.
 */
class H3AvailabilityTest {

    // Georgia Tech, the campus this app was built around.
    private val lat = 33.7756
    private val lon = -84.3963

    @Test
    fun aPointResolvesToACellAtTheResolutionsThisProjectUses() {
        // 8 is the parent resolution frozenApp drew its overlay at; 9 is the
        // child resolution the published schema stores.
        val res8 = H3.geoToH3(H3LatLng(lat, lon), 8)
        val res9 = H3.geoToH3(H3LatLng(lat, lon), 9)

        assertTrue(res8 != 0UL && res9 != 0UL, "both resolutions must index")
        assertTrue(res8 != res9, "different resolutions must give different cells")
    }

    @Test
    fun aCellYieldsSixVerticesAroundItsOwnCentre() {
        val cell = H3.geoToH3(H3LatLng(lat, lon), 8).toHexString()

        val boundary = H3.vertices(cell)

        // A hexagon, not a pentagon: the 12 pentagons sit in ocean, so anywhere
        // on land has six.
        assertEquals(6, boundary.size)
        // Every vertex should sit close to the point that generated the cell -
        // a resolution-8 cell has an edge of roughly 460m, well under 0.05 degrees.
        boundary.forEach { vertex ->
            assertTrue(abs(vertex.lat - lat) < 0.05, "vertex latitude ${vertex.lat} implausible")
            assertTrue(abs(vertex.lng - lon) < 0.05, "vertex longitude ${vertex.lng} implausible")
        }
    }

    @Test
    fun theSameCoordinateAlwaysGivesTheSameCell() {
        assertEquals(
            H3.geoToH3(H3LatLng(lat, lon), 8),
            H3.geoToH3(H3LatLng(lat, lon), 8),
        )
    }
}
