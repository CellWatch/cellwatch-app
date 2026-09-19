package edu.gatech.cc.cellwatch.domain.measurementhistory

import edu.gatech.cc.cellwatch.domain.sync.SyncStatusSummary

/**
 * Everything the history screen renders, in one immutable object.
 */
data class MeasurementHistoryUiState(
    val headerText: String,
    val emptyText: String?,
    val runRows: List<MeasurementHistoryRunRow>,
    val totalRunCount: Int,
    val selected: MeasurementHistoryRunSnapshot?,
    val selectedTitle: String,
    val selectedDetail: String,
    /** Sync wording, from the one presenter that owns it app-wide. */
    val syncHeadline: String,
    val syncDetail: String?,
    val showRetry: Boolean,
) {
    val isEmpty: Boolean get() = runRows.isEmpty()
}

/**
 * The single door for the history screen.
 *
 * Wraps [MeasurementHistoryViewController] and the sync summary rather than
 * letting the screen orchestrate them. The controller was written months ago
 * and never adopted: both platforms hand-rolled the same sort, the same
 * `take(5)`, the same "Run N: ... latency" string and the same selection
 * tracking beside it. Screens now get one object and render it.
 */
class MeasurementHistoryViewModel {
    /**
     * Owned rather than injected: Kotlin default arguments do not bridge to
     * Swift, so a defaulted constructor parameter makes `MeasurementHistoryViewModel()`
     * uncallable from the iOS shell.
     */
    private val controller = MeasurementHistoryViewController()

    private var syncSummary: SyncStatusSummary? = null
    private var pendingRecords: Int = 0

    fun currentState(): MeasurementHistoryUiState = project()

    fun onRunsLoaded(runs: List<MeasurementHistoryRunSnapshot>): MeasurementHistoryUiState {
        controller.loadSnapshots(runs)
        return project()
    }

    fun onSyncStatusLoaded(
        summary: SyncStatusSummary,
        pendingMeasurements: Int,
        pendingSubmissions: Int,
    ): MeasurementHistoryUiState {
        syncSummary = summary
        pendingRecords = pendingMeasurements + pendingSubmissions
        controller.updatePendingCounts(pendingMeasurements, pendingSubmissions)
        return project()
    }

    fun onRunSelected(timestampMs: Long): MeasurementHistoryUiState {
        controller.selectRun(timestampMs)
        return project()
    }

    private fun project(): MeasurementHistoryUiState {
        val state = controller.currentState()
        val selected = state.selectedRun
        return MeasurementHistoryUiState(
            headerText = if (state.totalRunCount == 0) {
                "No measurements yet"
            } else {
                // The list is capped; saying so stops "5 runs" reading as the total.
                "${state.totalRunCount} measurement${if (state.totalRunCount == 1) "" else "s"}" +
                    if (state.runRows.size < state.totalRunCount) {
                        " — showing the ${state.runRows.size} most recent"
                    } else {
                        ""
                    }
            },
            emptyText = if (state.totalRunCount == 0) {
                "Measurements you take will be listed here, newest first."
            } else {
                null
            },
            runRows = state.runRows,
            totalRunCount = state.totalRunCount,
            selected = selected,
            selectedTitle = if (selected == null) "Measurement details" else "Selected run",
            selectedDetail = selected?.let { run ->
                buildString {
                    appendLine("Latency: ${run.latency}")
                    appendLine("Download: ${run.download}")
                    appendLine("Upload: ${run.upload}")
                    append("Sync: ${run.uploaded}")
                }
            } ?: state.status.detail,
            syncHeadline = syncSummary?.headline ?: state.status.syncSummary,
            syncDetail = syncSummary?.detail,
            // Only offered when there is something to retry; a permanently
            // enabled Retry on an empty queue invites pointless taps.
            showRetry = pendingRecords > 0,
        )
    }
}
