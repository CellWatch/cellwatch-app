package edu.gatech.cc.cellwatch.domain.measurementhistory

data class MeasurementHistoryRunSnapshot(
    val timestampMs: Long,
    val latency: String,
    val download: String,
    val upload: String,
    val uploaded: String,
    val detail: String,
)

data class MeasurementHistoryRunRow(
    val timestampMs: Long,
    val summary: String,
    val selected: Boolean,
)

data class MeasurementHistoryViewState(
    val status: MeasurementHistoryStatusReadModel,
    val totalRunCount: Int,
    val runRows: List<MeasurementHistoryRunRow>,
    val selectedRun: MeasurementHistoryRunSnapshot?,
)

/**
 * Shared view-controller state for history screens.
 *
 * Contract:
 * - run-list count and pending-sync counts are intentionally independent datasets.
 * - pending counts report unsynced records only; they do not represent history row count.
 */
class MeasurementHistoryViewController(
    private val statusUseCase: MeasurementHistoryStatusUseCase = MeasurementHistoryStatusUseCase(),
    private val maxVisibleRows: Int = 5,
) {
    private var runs: List<MeasurementHistoryRunSnapshot> = emptyList()
    private var selectedTimestampMs: Long? = null
    private var pendingMeasurements: Int? = null
    private var pendingSubmissions: Int? = null

    fun reset(): MeasurementHistoryViewState {
        runs = emptyList()
        selectedTimestampMs = null
        pendingMeasurements = null
        pendingSubmissions = null
        return currentState()
    }

    fun loadSnapshots(snapshots: List<MeasurementHistoryRunSnapshot>): MeasurementHistoryViewState {
        runs = snapshots.sortedByDescending { it.timestampMs }
        selectedTimestampMs = selectedTimestampMs
            ?.takeIf { selected -> runs.any { it.timestampMs == selected } }
            ?: runs.firstOrNull()?.timestampMs
        return currentState()
    }

    fun selectRun(timestampMs: Long): MeasurementHistoryViewState {
        if (runs.any { it.timestampMs == timestampMs }) {
            selectedTimestampMs = timestampMs
        }
        return currentState()
    }

    fun updatePendingCounts(measurements: Int?, submissions: Int?): MeasurementHistoryViewState {
        pendingMeasurements = measurements
        pendingSubmissions = submissions
        return currentState()
    }

    fun currentState(): MeasurementHistoryViewState {
        val latest = runs.firstOrNull()
        val selected = runs.firstOrNull { it.timestampMs == selectedTimestampMs } ?: latest

        val normalizedMeasurements = pendingMeasurements?.coerceAtLeast(0)
        val normalizedSubmissions = pendingSubmissions?.coerceAtLeast(0)

        val status = statusUseCase.present(
            MeasurementHistoryStatusInput(
                recentMeasurements = emptyList(),
                pendingMeasurements = normalizedMeasurements,
                pendingSubmissions = normalizedSubmissions,
                fallbackLatencyText = latest?.latency,
                fallbackDownloadText = latest?.download,
                fallbackUploadText = latest?.upload,
                fallbackUploadedText = latest?.uploaded,
                fallbackDetailText = latest?.detail,
            ),
        )

        val rows = runs
            .take(maxVisibleRows)
            .mapIndexed { index, run ->
                MeasurementHistoryRunRow(
                    timestampMs = run.timestampMs,
                    summary = "Run ${index + 1}: ${run.latency} latency, ${run.download} download, ${run.upload} upload",
                    selected = run.timestampMs == selected?.timestampMs,
                )
            }

        return MeasurementHistoryViewState(
            status = status,
            totalRunCount = runs.size,
            runRows = rows,
            selectedRun = selected,
        )
    }
}
