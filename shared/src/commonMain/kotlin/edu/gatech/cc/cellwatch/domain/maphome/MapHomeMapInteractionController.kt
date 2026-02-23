package edu.gatech.cc.cellwatch.domain.maphome

enum class MapHomeOverlayMode {
    HEX_GRID,
    POINTS,
}

data class MapHomeMapInteractionState(
    val zoomLevel: Double = 13.0,
    val overlayMode: MapHomeOverlayMode = MapHomeOverlayMode.HEX_GRID,
    val showHexGrid: Boolean = true,
    val showPoints: Boolean = false,
    val selectedItemId: String? = null,
    val selectedItemTitle: String? = null,
)

/**
 * Shared, SDK-agnostic map interaction reducer.
 *
 * Platform SDK adapters (Android/iOS Mapbox) should consume this state to keep:
 * - zoom-driven overlay switching behavior
 * - selection semantics
 * consistent across platforms.
 */
class MapHomeMapInteractionController(
    private val minHexGridZoom: Double = 12.0,
) {
    private var state = MapHomeMapInteractionState()
    private var preferredOverlayMode = MapHomeOverlayMode.HEX_GRID

    fun currentState(): MapHomeMapInteractionState = state

    fun reset(): MapHomeMapInteractionState {
        preferredOverlayMode = MapHomeOverlayMode.HEX_GRID
        state = reduce(
            zoomLevel = 13.0,
            preferred = preferredOverlayMode,
            selectedItemId = null,
            selectedItemTitle = null,
        )
        return state
    }

    fun onZoomChanged(zoomLevel: Double): MapHomeMapInteractionState {
        state = reduce(
            zoomLevel = zoomLevel,
            preferred = preferredOverlayMode,
            selectedItemId = state.selectedItemId,
            selectedItemTitle = state.selectedItemTitle,
        )
        return state
    }

    fun setPreferredOverlayMode(mode: MapHomeOverlayMode): MapHomeMapInteractionState {
        preferredOverlayMode = mode
        state = reduce(
            zoomLevel = state.zoomLevel,
            preferred = preferredOverlayMode,
            selectedItemId = state.selectedItemId,
            selectedItemTitle = state.selectedItemTitle,
        )
        return state
    }

    fun selectItem(itemId: String, title: String): MapHomeMapInteractionState {
        state = state.copy(selectedItemId = itemId, selectedItemTitle = title)
        return state
    }

    fun clearSelection(): MapHomeMapInteractionState {
        state = state.copy(selectedItemId = null, selectedItemTitle = null)
        return state
    }

    private fun reduce(
        zoomLevel: Double,
        preferred: MapHomeOverlayMode,
        selectedItemId: String?,
        selectedItemTitle: String?,
    ): MapHomeMapInteractionState {
        val normalizedZoom = zoomLevel.coerceAtLeast(0.0)
        val effectiveMode = if (preferred == MapHomeOverlayMode.HEX_GRID && normalizedZoom < minHexGridZoom) {
            MapHomeOverlayMode.POINTS
        } else {
            preferred
        }
        return MapHomeMapInteractionState(
            zoomLevel = normalizedZoom,
            overlayMode = effectiveMode,
            showHexGrid = effectiveMode == MapHomeOverlayMode.HEX_GRID,
            showPoints = effectiveMode == MapHomeOverlayMode.POINTS,
            selectedItemId = selectedItemId,
            selectedItemTitle = selectedItemTitle,
        )
    }
}
