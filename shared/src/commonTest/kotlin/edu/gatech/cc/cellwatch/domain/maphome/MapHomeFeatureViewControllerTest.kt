package edu.gatech.cc.cellwatch.domain.maphome

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MapHomeFeatureViewControllerTest {
    @Test
    fun emptySnapshots_reportsNoLocations() {
        val controller = MapHomeFeatureViewController()

        val state = controller.loadMeasurements(emptyList())

        assertFalse(state.hasAnyLocationData)
        assertTrue(state.points.isEmpty())
        assertTrue(state.hexCells.isEmpty())
        assertNull(state.centerLatitude)
        assertNull(state.centerLongitude)
    }

    @Test
    fun validSnapshots_buildsPointsAndCells() {
        val controller = MapHomeFeatureViewController(
            coarseGridDegrees = 0.1,
            fineGridDegrees = 0.05,
            fineGridZoomThreshold = 12.0,
        )

        val state = controller.loadMeasurements(
            listOf(
                MapHomeMeasurementLocationSnapshot(
                    id = "run-1",
                    title = "Run 1",
                    timestampMs = 3,
                    latitude = 33.7490,
                    longitude = -84.3880,
                ),
                MapHomeMeasurementLocationSnapshot(
                    id = "run-2",
                    title = "Run 2",
                    timestampMs = 2,
                    latitude = 33.7500,
                    longitude = -84.3870,
                ),
                MapHomeMeasurementLocationSnapshot(
                    id = "run-3",
                    title = "Run 3",
                    timestampMs = 1,
                    latitude = 34.0500,
                    longitude = -84.1000,
                ),
            ),
        )

        assertTrue(state.hasAnyLocationData)
        assertEquals(3, state.points.size)
        assertEquals(3, state.hexCells.size)
        assertTrue(state.summary.contains("3 point(s)"))
    }

    @Test
    fun missingCoordinates_areDroppedFromFeatureSet() {
        val controller = MapHomeFeatureViewController()

        val state = controller.loadMeasurements(
            listOf(
                MapHomeMeasurementLocationSnapshot(
                    id = "run-1",
                    title = "Run 1",
                    timestampMs = 1,
                    latitude = Double.NaN,
                    longitude = -84.0,
                ),
                MapHomeMeasurementLocationSnapshot(
                    id = "run-2",
                    title = "Run 2",
                    timestampMs = 2,
                    latitude = 33.8,
                    longitude = -84.2,
                ),
            ),
        )

        assertEquals(1, state.points.size)
        assertEquals("run-2", state.points.first().id)
    }

    @Test
    fun zoomControlsAggregationResolution() {
        val controller = MapHomeFeatureViewController(
            coarseGridDegrees = 0.08,
            fineGridDegrees = 0.01,
            fineGridZoomThreshold = 12.0,
        )
        controller.loadMeasurements(
            listOf(
                MapHomeMeasurementLocationSnapshot("run-1", "Run 1", 2, 33.750, -84.390),
                MapHomeMeasurementLocationSnapshot("run-2", "Run 2", 1, 33.756, -84.394),
            ),
        )

        val coarse = controller.onZoomChanged(10.0)
        val fine = controller.onZoomChanged(13.0)

        assertEquals(1, coarse.hexCells.size)
        assertEquals(2, fine.hexCells.size)
    }
}
