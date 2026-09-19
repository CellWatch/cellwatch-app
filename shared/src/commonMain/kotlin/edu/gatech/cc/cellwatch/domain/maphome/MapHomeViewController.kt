package edu.gatech.cc.cellwatch.domain.maphome

import edu.gatech.cc.cellwatch.domain.sync.SyncStatusSummary
import edu.gatech.cc.cellwatch.domain.sync.SyncStatusTone

data class MapHomeInput(
    val onboardingComplete: Boolean,
    val recentRunCount: Int,
    val pendingCountsKnown: Boolean,
    val pendingMeasurements: Int,
    val pendingSubmissions: Int,
    /**
     * The full sync story, when the caller has it.
     *
     * Optional so the harness paths that only know queue counts keep working.
     * When present it wins: one presenter owning every sync sentence is what
     * stops the map and the results screen describing the same upload
     * differently.
     */
    val syncStatus: SyncStatusSummary? = null,
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
/**
 * Legacy direct-access controller. **Product screens must not use this** - they
 * use [MapHomeViewModel], which is the screen's single entry point (Rule 1 in
 * APP_LAYER_ARCHITECTURE_CONTRACT.md).
 *
 * Still public only because the test harnesses construct it directly, and those
 * are deliberately left alone. It is not duplicated logic: [MapHomeViewModel]
 * wraps this same instance rather than reimplementing it.
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

        val status = input.syncStatus
        val syncSummary = if (status != null) {
            listOfNotNull(status.headline, status.detail).joinToString(" ")
        } else {
            when (syncState) {
                // Retained for the harness, which has counts but no status
                // record. The product shell always supplies the status.
                MapHomeSyncStateKey.UNKNOWN -> "Sync status unknown."
                MapHomeSyncStateKey.SYNCED -> "All records are synced."
                MapHomeSyncStateKey.PENDING ->
                    "Pending sync queue: $pendingMeasurements measurement record(s), " +
                        "$pendingSubmissions submission record(s)."
            }
        }
        val resolvedSyncState = when (status?.tone) {
            SyncStatusTone.SUCCESS -> MapHomeSyncStateKey.SYNCED
            SyncStatusTone.WARNING -> MapHomeSyncStateKey.PENDING
            SyncStatusTone.NEUTRAL -> MapHomeSyncStateKey.UNKNOWN
            null -> syncState
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
            syncStateKey = resolvedSyncState,
            canStartMeasurement = input.onboardingComplete,
        )
    }
}
