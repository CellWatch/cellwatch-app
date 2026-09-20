package edu.gatech.cc.cellwatch.domain.maphome

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Exercises the real H3 path through the feature reducer.
 *
 * iOS-only for the same reason as [H3AvailabilityTest]: h3-kmp publishes
 * android and ios variants and no jvm one, so `commonTest` would run against
 * the unsupported stub and prove nothing.
 */
class MapHomeHexAggregationTest {

    private val lat = 33.7756
    private val lon = -84.3963

    private fun snapshot(id: String, lat: Double, lon: Double) =
        MapHomeMeasurementLocationSnapshot(
            id = id,
            title = "latency",
            timestampMs = 1_700_000_000_000,
            latitude = lat,
            longitude = lon,
        )

    @Test
    fun aCellCarriesTheBoundaryNeededToDrawIt() {
        val controller = MapHomeFeatureViewController()

        val state = controller.loadMeasurements(listOf(snapshot("a", lat, lon)))

        assertEquals(1, state.hexCells.size)
        val cell = state.hexCells.single()
        // Six vertices, and flagged as drawable - the thing that was missing
        // and made the overlay impossible however the icon was configured.
        assertEquals(6, cell.boundary.size)
        assertTrue(cell.hasBoundary)
    }

    @Test
    fun theCellCentreSitsInsideItsOwnOutline() {
        val controller = MapHomeFeatureViewController()

        val cell = controller.loadMeasurements(listOf(snapshot("a", lat, lon))).hexCells.single()

        // Derived from the boundary, not from the members: a cell centred on
        // clustered points would sit off its own outline.
        val minLat = cell.boundary.minOf { it.latitude }
        val maxLat = cell.boundary.maxOf { it.latitude }
        assertTrue(cell.centerLatitude in minLat..maxLat, "centre ${cell.centerLatitude} outside outline")
        val minLon = cell.boundary.minOf { it.longitude }
        val maxLon = cell.boundary.maxOf { it.longitude }
        assertTrue(cell.centerLongitude in minLon..maxLon, "centre ${cell.centerLongitude} outside outline")
    }

    @Test
    fun nearbyPointsShareOneCellAndFarPointsDoNot() {
        val controller = MapHomeFeatureViewController()

        // Roughly 10m apart: well inside one resolution-9 cell.
        val together = controller.loadMeasurements(
            listOf(snapshot("a", lat, lon), snapshot("b", lat + 0.0001, lon)),
        )
        assertEquals(1, together.hexCells.size)
        assertEquals(2, together.hexCells.single().measurementCount)

        // Roughly 11km away.
        val apart = controller.loadMeasurements(
            listOf(snapshot("a", lat, lon), snapshot("c", lat + 0.1, lon)),
        )
        assertEquals(2, apart.hexCells.size)
    }

    @Test
    fun zoomingOutAggregatesIntoLargerCells() {
        val controller = MapHomeFeatureViewController()
        // About 700m apart: separate at resolution 9, likely shared at 8.
        val points = listOf(snapshot("a", lat, lon), snapshot("b", lat + 0.006, lon))

        controller.loadMeasurements(points)
        val fine = controller.onZoomChanged(14.0).hexCells.size
        val coarse = controller.onZoomChanged(10.0).hexCells.size

        assertTrue(coarse <= fine, "zooming out produced more cells ($coarse) than in ($fine)")
    }
}
