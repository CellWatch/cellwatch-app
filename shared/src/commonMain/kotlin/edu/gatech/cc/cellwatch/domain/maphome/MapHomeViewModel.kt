package edu.gatech.cc.cellwatch.domain.maphome

/**
 * Everything the map-home screen renders, in one immutable object.
 *
 * Copy, map interaction and map features were previously three separate
 * controllers that each platform had to construct and orchestrate itself. That
 * is how the two apps drifted: the composing happened in the platform layer,
 * twice, differently.
 */
data class MapHomeUiState(
    // Copy and status
    val title: String,
    val subtitle: String,
    val mapPanelTitle: String,
    val mapPanelBody: String,
    val statusText: String,
    val syncSummary: String,
    val syncStateKey: MapHomeSyncStateKey,
    val canStartMeasurement: Boolean,
    // Map interaction
    val zoomLevel: Double,
    val overlayMode: MapHomeOverlayMode,
    val showHexGrid: Boolean,
    val showPoints: Boolean,
    val selectedItemId: String?,
    val selectedItemTitle: String?,
    // Map features
    val points: List<MapHomePointFeature>,
    val hexCells: List<MapHomeHexCellFeature>,
)

/**
 * The single entry point for the map-home screen (Rule 1).
 *
 * The three controllers it wraps are `internal`, so a platform cannot reach
 * past this and start composing its own version - which is what the rule
 * exists to prevent. Their logic and tests are unchanged; only the door moved.
 */
class MapHomeViewModel(
    minHexGridZoom: Double = 0.0,
) {
    private val presenter = MapHomeViewController()
    private val interaction = MapHomeMapInteractionController(minHexGridZoom = minHexGridZoom)
    private val features = MapHomeFeatureViewController()

    private var lastInput = MapHomeInput(
        onboardingComplete = false,
        recentRunCount = 0,
        pendingCountsKnown = false,
        pendingMeasurements = 0,
        pendingSubmissions = 0,
    )

    fun currentState(): MapHomeUiState = combine()

    /** Applies the shell inputs - onboarding state, run counts, pending sync. */
    fun onInputChanged(input: MapHomeInput): MapHomeUiState {
        lastInput = input
        return combine()
    }

    /** Supplies the measurement locations the map draws. */
    fun onMeasurementsLoaded(
        snapshots: List<MapHomeMeasurementLocationSnapshot>,
    ): MapHomeUiState {
        features.loadMeasurements(snapshots)
        return combine()
    }

    /** The visible rectangle, so the overlay can tile it. */
    fun onBoundsChanged(north: Double, south: Double, east: Double, west: Double): MapHomeUiState {
        features.onBoundsChanged(north = north, south = south, east = east, west = west)
        return combine()
    }

    fun onZoomChanged(zoomLevel: Double): MapHomeUiState {
        interaction.onZoomChanged(zoomLevel)
        // Features are zoom-dependent too: which overlay is meaningful changes
        // with scale, so both must see the same zoom or the panel count and the
        // drawn overlay disagree.
        features.onZoomChanged(zoomLevel)
        return combine()
    }

    fun setOverlayMode(mode: MapHomeOverlayMode): MapHomeUiState {
        interaction.setPreferredOverlayMode(mode)
        return combine()
    }

    fun selectItem(itemId: String, title: String): MapHomeUiState {
        interaction.selectItem(itemId, title)
        return combine()
    }

    fun clearSelection(): MapHomeUiState {
        interaction.clearSelection()
        return combine()
    }

    fun reset(): MapHomeUiState {
        interaction.reset()
        features.reset()
        return combine()
    }

    private fun combine(): MapHomeUiState {
        val presented = presenter.present(lastInput)
        val interactionState = interaction.currentState()
        val featureState = features.currentState()
        return MapHomeUiState(
            title = presented.title,
            subtitle = presented.subtitle,
            mapPanelTitle = presented.mapPanelTitle,
            mapPanelBody = presented.mapPanelBody,
            statusText = presented.statusText,
            syncSummary = presented.syncSummary,
            syncStateKey = presented.syncStateKey,
            canStartMeasurement = presented.canStartMeasurement,
            zoomLevel = interactionState.zoomLevel,
            overlayMode = interactionState.overlayMode,
            showHexGrid = interactionState.showHexGrid,
            showPoints = interactionState.showPoints,
            selectedItemId = interactionState.selectedItemId,
            selectedItemTitle = interactionState.selectedItemTitle,
            points = featureState.points,
            hexCells = featureState.hexCells,
        )
    }
}
