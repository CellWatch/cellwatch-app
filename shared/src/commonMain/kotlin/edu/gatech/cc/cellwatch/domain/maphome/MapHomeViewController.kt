package edu.gatech.cc.cellwatch.domain.maphome

data class MapHomeInput(
    val onboardingComplete: Boolean,
    val recentRunCount: Int,
    val pendingCountsKnown: Boolean,
    val pendingMeasurements: Int,
    val pendingSubmissions: Int,
)

enum class MapHomeSyncStateKey {
    UNKNOWN,
    SYNCED,
    PENDING,
}

data class MapHomeViewState(
    val title: String,
    val subtitle: String,
    val mapPanelTitle: String,
    val mapPanelBody: String,
    val statusText: String,
    val syncSummary: String,
    val syncStateKey: MapHomeSyncStateKey,
    val canStartMeasurement: Boolean,
)

/**
 * Shared presenter/controller contract for the map-home shell.
 *
 * This keeps top-level map-home copy and state semantics aligned across Android and iOS
 * while map rendering itself remains platform-specific.
 */
class MapHomeViewController {
    fun present(input: MapHomeInput): MapHomeViewState {
        val recentRunCount = input.recentRunCount.coerceAtLeast(0)
        val pendingMeasurements = input.pendingMeasurements.coerceAtLeast(0)
        val pendingSubmissions = input.pendingSubmissions.coerceAtLeast(0)
        val pendingTotal = pendingMeasurements + pendingSubmissions

        val syncState = when {
            !input.pendingCountsKnown -> MapHomeSyncStateKey.UNKNOWN
            pendingTotal <= 0 -> MapHomeSyncStateKey.SYNCED
            else -> MapHomeSyncStateKey.PENDING
        }

        val syncSummary = when (syncState) {
            MapHomeSyncStateKey.UNKNOWN -> "Sync status unknown. Open History to refresh."
            MapHomeSyncStateKey.SYNCED -> "All records are synced."
            MapHomeSyncStateKey.PENDING ->
                "Pending sync queue: $pendingMeasurements measurement record(s), " +
                    "$pendingSubmissions submission record(s)."
        }

        val mapPanelBody = when {
            recentRunCount <= 0 -> "No saved measurements yet. Take a measurement to populate the map."
            recentRunCount == 1 -> "Showing 1 saved run on the map."
            else -> "Showing $recentRunCount saved runs on the map (newest first)."
        }

        val statusText = when {
            !input.onboardingComplete -> "Complete your profile before taking a measurement."
            recentRunCount <= 0 -> "Profile saved. Start a measurement when ready."
            syncState == MapHomeSyncStateKey.PENDING -> "Recent measurements available. Some uploads are pending."
            syncState == MapHomeSyncStateKey.SYNCED -> "Recent measurements available and synced."
            else -> "Recent measurements available."
        }

        return MapHomeViewState(
            title = "CellWatch",
            subtitle = "Map home",
            mapPanelTitle = "Measurement map",
            mapPanelBody = mapPanelBody,
            statusText = statusText,
            syncSummary = syncSummary,
            syncStateKey = syncState,
            canStartMeasurement = input.onboardingComplete,
        )
    }
}
