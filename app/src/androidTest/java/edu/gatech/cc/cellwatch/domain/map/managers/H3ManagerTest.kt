package edu.gatech.cc.cellwatch.domain.map.managers

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mapbox.geojson.Point
import com.mapbox.maps.CoordinateBounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * H3Manager with the libh3-java.so the app ships. Every expected value came from H3 3.7.2, the
 * version before the move to H3 4 for 16 KB pages, so the map draws the same hexagons as before.
 */
@RunWith(AndroidJUnit4::class)
class H3ManagerTest {
    // The location the other tests use, in Washington, DC, and its hexagons.
    private val lat = 38.8860976
    private val lon = -76.9962846
    private val large = 0x882aa8459dfffffL
    private val small = 0x892aa845953ffffL

    @Test
    fun pointsAreInTheSameHexagonsAsBefore() {
        assertEquals(large, H3Manager.getH3Index(lat, lon, H3Manager.PARENT_HEX_RES))
        assertEquals(small, H3Manager.getH3Index(lat, lon, H3Manager.CHILD_HEX_RES))
        assertEquals(0x8944c1a88d3ffffL, H3Manager.getH3Index(33.7756, -84.3963, H3Manager.CHILD_HEX_RES))
        assertEquals(0x89be0e35cbbffffL, H3Manager.getH3Index(-33.8688, 151.2093, H3Manager.CHILD_HEX_RES))
        assertEquals(H3Manager.PARENT_HEX_RES, H3Manager.getH3ResolutionFromAddress(large))
        assertEquals(H3Manager.CHILD_HEX_RES, H3Manager.getH3ResolutionFromAddress(small))
        assertTrue(H3Manager.isInH3Hex(lat, lon, small))
        assertFalse(H3Manager.isInH3Hex(33.7756, -84.3963, small))
    }

    @Test
    fun relatedHexagonsAreTheSameAsBefore() {
        assertEquals(
            setOf(
                0x892aa8459c3ffffL, 0x892aa8459c7ffffL, 0x892aa8459cbffffL, 0x892aa8459cfffffL,
                0x892aa8459d3ffffL, 0x892aa8459d7ffffL, 0x892aa8459dbffffL,
            ),
            H3Manager.getRelatedH3Hex(large, H3Manager.CHILD_HEX_RES).toSet(),
        )
        // Small hexagons don't nest exactly in large ones: this one's parent isn't the large
        // hexagon the location is in.
        assertEquals(listOf(0x882aa84595fffffL), H3Manager.getRelatedH3Hex(small, H3Manager.PARENT_HEX_RES))
        assertEquals(listOf(small), H3Manager.getRelatedH3Hex(small, H3Manager.CHILD_HEX_RES))
    }

    @Test
    fun aHexagonsCentreAndOutlineAreTheSameAsBefore() {
        val centre = H3Manager.getH3CenterFromAddressSingleton(small)
        assertEquals(38.885305946955725, centre.latitude(), 1e-9)
        assertEquals(-76.99761019383095, centre.longitude(), 1e-9)

        val corners = listOf(
            38.88702195031515 to -76.99778515411245,
            38.88605316709795 to -76.9997504030873,
            38.88433718950104 to -76.99957537563381,
            38.88358998393829 to -76.99743524614065,
            38.88455869552994 to -76.9954700518789,
            38.88627468430729 to -76.99564493239578,
        )
        val outline = H3Manager.getH3BoundaryFromAddressSingleton(small).single()
        // GeoJSON closes a polygon by repeating its first point.
        assertEquals(corners.size + 1, outline.size)
        (corners + corners.first()).zip(outline).forEach { (corner, point) ->
            assertEquals(corner.first, point.latitude(), 1e-9)
            assertEquals(corner.second, point.longitude(), 1e-9)
        }
    }

    @Test
    fun aViewIsCoveredByTheSameHexagonsAsBefore() {
        // About 2.3 by 2.8 km around Georgia Tech.
        val view = CoordinateBounds(Point.fromLngLat(-84.410, 33.765), Point.fromLngLat(-84.385, 33.790))

        val hexagons = H3Manager.getH3OverlayAddressesFromBounds(view, H3Manager.PARENT_HEX_RES)

        assertEquals(
            listOf(
                0x8844c1a881fffffL, 0x8844c1a885fffffL, 0x8844c1a889fffffL, 0x8844c1a88bfffffL,
                0x8844c1a88dfffffL, 0x8844c1a8c7fffffL, 0x8844c1a8e3fffffL, 0x8844c1a8ebfffffL,
            ),
            hexagons.sorted(),
        )
    }
}
