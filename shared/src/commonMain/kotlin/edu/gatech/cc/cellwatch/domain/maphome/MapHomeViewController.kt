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
                MapHomeSyncStateKey.UNKNOWN -> MapHomeCopy.SYNC_UNKNOWN
                MapHomeSyncStateKey.SYNCED -> MapHomeCopy.ALL_SYNCED
                MapHomeSyncStateKey.PENDING ->
                    MapHomeCopy.pendingQueue(pendingMeasurements, pendingSubmissions)
            }
        }
        val resolvedSyncState = when (status?.tone) {
            SyncStatusTone.SUCCESS -> MapHomeSyncStateKey.SYNCED
            SyncStatusTone.WARNING -> MapHomeSyncStateKey.PENDING
            SyncStatusTone.NEUTRAL -> MapHomeSyncStateKey.UNKNOWN
            null -> syncState
        }

        val mapPanelBody = when {
            recentRunCount <= 0 -> MapHomeCopy.NO_SAVED_MEASUREMENTS
            else -> MapHomeCopy.showingRuns(recentRunCount)
        }

        val statusText = when {
            !input.onboardingComplete -> MapHomeCopy.COMPLETE_PROFILE_FIRST
            recentRunCount <= 0 -> MapHomeCopy.PROFILE_SAVED
            syncState == MapHomeSyncStateKey.PENDING -> MapHomeCopy.RECENT_PENDING
            syncState == MapHomeSyncStateKey.SYNCED -> MapHomeCopy.RECENT_SYNCED
            else -> MapHomeCopy.RECENT_AVAILABLE
        }

        return MapHomeViewState(
            title = MapHomeCopy.TITLE,
            subtitle = MapHomeCopy.SUBTITLE,
            mapPanelTitle = MapHomeCopy.MAP_PANEL_TITLE,
            mapPanelBody = mapPanelBody,
            statusText = statusText,
            syncSummary = syncSummary,
            syncStateKey = resolvedSyncState,
            canStartMeasurement = input.onboardingComplete,
        )
    }
}
