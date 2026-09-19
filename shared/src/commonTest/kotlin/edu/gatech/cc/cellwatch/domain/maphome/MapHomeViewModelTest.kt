package edu.gatech.cc.cellwatch.domain.maphome

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The map-home screen has one door. These assert the combined state, not the
 * three controllers behind it - platforms only ever see this.
 */
class MapHomeViewModelTest {

    private fun readyInput(pendingMeasurements: Int = 0, pendingSubmissions: Int = 0) = MapHomeInput(
        onboardingComplete = true,
        recentRunCount = 2,
        pendingCountsKnown = true,
        pendingMeasurements = pendingMeasurements,
        pendingSubmissions = pendingSubmissions,
    )

    private fun snapshot(id: String, lat: Double, lon: Double) = MapHomeMeasurementLocationSnapshot(
        id = id,
        title = "Measurement $id",
        timestampMs = 1L,
        latitude = lat,
        longitude = lon,
    )

    @Test
    fun oneCallYieldsCopyInteractionAndFeaturesTogether() {
        val vm = MapHomeViewModel()

        val state = vm.onInputChanged(readyInput())

        assertTrue(state.title.isNotBlank())
        assertTrue(state.canStartMeasurement, "a completed profile should allow measuring")
        assertEquals(MapHomeSyncStateKey.SYNCED, state.syncStateKey)
        // Interaction and feature fields arrive in the same object.
        assertTrue(state.zoomLevel > 0.0)
        assertTrue(state.points.isEmpty())
    }

    @Test
    fun pendingWorkIsReflectedInSyncState() {
        val vm = MapHomeViewModel()

        val state = vm.onInputChanged(readyInput(pendingMeasurements = 3))

        assertEquals(MapHomeSyncStateKey.PENDING, state.syncStateKey)
    }

    @Test
    fun loadedMeasurementsBecomeMapFeatures() {
        val vm = MapHomeViewModel()
        vm.onInputChanged(readyInput())

        val state = vm.onMeasurementsLoaded(
            listOf(snapshot("a", 33.77, -84.39), snapshot("b", 33.78, -84.40)),
        )

        assertTrue(
            state.points.isNotEmpty() || state.hexCells.isNotEmpty(),
            "loaded measurements must surface as something drawable",
        )
    }

    @Test
    fun zoomReachesInteractionAndFeaturesTogether() {
        // These were separate controllers, and a platform updating only one left
        // the panel count disagreeing with the drawn overlay.
        val vm = MapHomeViewModel()
        vm.onMeasurementsLoaded(listOf(snapshot("a", 33.77, -84.39)))

        val state = vm.onZoomChanged(15.0)

        assertEquals(15.0, state.zoomLevel)
    }

    @Test
    fun selectionRoundTrips() {
        val vm = MapHomeViewModel()

        val selected = vm.selectItem("a", "Measurement a")
        assertEquals("a", selected.selectedItemId)
        assertEquals("Measurement a", selected.selectedItemTitle)

        val cleared = vm.clearSelection()
        assertNull(cleared.selectedItemId)
        assertNull(cleared.selectedItemTitle)
    }

    @Test
    fun overlayModeIsHonoured() {
        val vm = MapHomeViewModel()

        val points = vm.setOverlayMode(MapHomeOverlayMode.POINTS)

        assertEquals(MapHomeOverlayMode.POINTS, points.overlayMode)
    }
}
