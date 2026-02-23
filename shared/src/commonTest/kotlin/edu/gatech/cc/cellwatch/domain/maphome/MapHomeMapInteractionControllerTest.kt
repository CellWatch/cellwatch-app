package edu.gatech.cc.cellwatch.domain.maphome

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MapHomeMapInteractionControllerTest {
    @Test
    fun reset_defaultsToHexGridAtMapHomeZoom() {
        val controller = MapHomeMapInteractionController(minHexGridZoom = 12.0)

        val state = controller.reset()

        assertEquals(13.0, state.zoomLevel)
        assertEquals(MapHomeOverlayMode.HEX_GRID, state.overlayMode)
        assertTrue(state.showHexGrid)
        assertFalse(state.showPoints)
        assertNull(state.selectedItemId)
    }

    @Test
    fun zoomBelowThreshold_fallsBackToPoints_whenHexPreferred() {
        val controller = MapHomeMapInteractionController(minHexGridZoom = 12.0)
        controller.reset()

        val state = controller.onZoomChanged(11.0)

        assertEquals(MapHomeOverlayMode.POINTS, state.overlayMode)
        assertFalse(state.showHexGrid)
        assertTrue(state.showPoints)
    }

    @Test
    fun pointsPreferred_staysPointsRegardlessOfZoom() {
        val controller = MapHomeMapInteractionController(minHexGridZoom = 12.0)
        controller.reset()
        controller.setPreferredOverlayMode(MapHomeOverlayMode.POINTS)

        val low = controller.onZoomChanged(8.0)
        val high = controller.onZoomChanged(16.0)

        assertEquals(MapHomeOverlayMode.POINTS, low.overlayMode)
        assertEquals(MapHomeOverlayMode.POINTS, high.overlayMode)
    }

    @Test
    fun selection_roundTripsAcrossZoomChanges() {
        val controller = MapHomeMapInteractionController(minHexGridZoom = 12.0)
        controller.reset()
        controller.selectItem(itemId = "group-1", title = "Run group")

        val afterZoom = controller.onZoomChanged(10.0)
        assertEquals("group-1", afterZoom.selectedItemId)
        assertEquals("Run group", afterZoom.selectedItemTitle)

        val cleared = controller.clearSelection()
        assertNull(cleared.selectedItemId)
        assertNull(cleared.selectedItemTitle)
    }
}
