package edu.gatech.cc.cellwatch.domain.measurementhistory

import edu.gatech.cc.cellwatch.domain.measurementrun.MeasurementResultReadModelUseCase
import edu.gatech.cc.cellwatch.domain.measurementrun.MeasurementRunProgress
import edu.gatech.cc.cellwatch.domain.measurementrun.MeasurementRunState
import edu.gatech.cc.cellwatch.domain.model.Measurement
import edu.gatech.cc.cellwatch.domain.model.MeasurementGroup

data class MeasurementHistoryStatusInput(
    val recentMeasurements: List<Measurement> = emptyList(),
    val pendingMeasurements: Int? = null,
    val pendingSubmissions: Int? = null,
    val fallbackLatencyText: String? = null,
    val fallbackDownloadText: String? = null,
    val fallbackUploadText: String? = null,
    val fallbackUploadedText: String? = null,
    val fallbackDetailText: String? = null,
)

data class MeasurementHistoryStatusReadModel(
    val stateKey: MeasurementHistoryStateKey,
    val syncStateKey: MeasurementHistorySyncStateKey,
    val title: String,
    val detail: String,
    val latencyText: String,
    val downloadText: String,
    val uploadText: String,
    val uploadedText: String,
    val syncSummary: String,
    val hasMeasurement: Boolean,
)

enum class MeasurementHistoryStateKey {
    EMPTY,
    HAS_MEASUREMENT,
}

enum class MeasurementHistorySyncStateKey {
    UNKNOWN,
    PENDING,
    SYNCED,
}

class MeasurementHistoryStatusUseCase(
    private val measurementResultReadModelUseCase: MeasurementResultReadModelUseCase = MeasurementResultReadModelUseCase(),
) {
    fun present(input: MeasurementHistoryStatusInput): MeasurementHistoryStatusReadModel {
        val latestGroup = latestMeasurementGroup(input.recentMeasurements)
        val hasMeasurement = latestGroup != null
        val runState = if (latestGroup == null) {
            null
        } else {
            MeasurementRunState(
                progress = MeasurementRunProgress.END,
                results = latestGroup,
                uploadTime = latestGroup.latency?.uploadTime
                    ?: latestGroup.download?.uploadTime
                    ?: latestGroup.upload?.uploadTime,
            )
        }
        val metrics = if (runState == null) {
            null
        } else {
            measurementResultReadModelUseCase.present(runState)
        }
        val sync = pendingSummary(
            measurements = input.pendingMeasurements,
            submissions = input.pendingSubmissions,
        )
        val hasFallback = !input.fallbackLatencyText.isNullOrBlank() ||
            !input.fallbackDownloadText.isNullOrBlank() ||
            !input.fallbackUploadText.isNullOrBlank()
        return if ((!hasMeasurement || metrics == null) && !hasFallback) {
            MeasurementHistoryStatusReadModel(
                stateKey = MeasurementHistoryStateKey.EMPTY,
                syncStateKey = sync.stateKey,
                title = "No measurements yet",
                detail = "Run your first measurement to populate history.",
                latencyText = "--",
                downloadText = "--",
                uploadText = "--",
                uploadedText = "--",
                syncSummary = sync.summary,
                hasMeasurement = false,
            )
        } else {
            val latencyText = metrics?.latencyText ?: input.fallbackLatencyText.orEmpty().ifBlank { "--" }
            val downloadText = metrics?.downloadText ?: input.fallbackDownloadText.orEmpty().ifBlank { "--" }
            val uploadText = metrics?.uploadText ?: input.fallbackUploadText.orEmpty().ifBlank { "--" }
            val uploadedText = metrics?.uploadedText ?: input.fallbackUploadedText.orEmpty().ifBlank { "Pending sync" }
            val detail = metrics?.summaryText ?: input.fallbackDetailText.orEmpty().ifBlank { "Latest measurement captured." }
            MeasurementHistoryStatusReadModel(
                stateKey = MeasurementHistoryStateKey.HAS_MEASUREMENT,
                syncStateKey = sync.stateKey,
                title = "Latest measurement",
                detail = detail,
                latencyText = latencyText,
                downloadText = downloadText,
                uploadText = uploadText,
                uploadedText = uploadedText,
                syncSummary = sync.summary,
                hasMeasurement = true,
            )
        }
    }

    private fun latestMeasurementGroup(recentMeasurements: List<Measurement>): MeasurementGroup? {
        if (recentMeasurements.isEmpty()) return null
        val latest = recentMeasurements.first()
        val groupToken = latest.groupId ?: latest.id
        val groupRows = recentMeasurements.filter { (it.groupId ?: it.id) == groupToken }
        if (groupRows.isEmpty()) return null
        var latency: Measurement? = null
        var download: Measurement? = null
        var upload: Measurement? = null
        groupRows.forEach { measurement ->
            when (measurement.type) {
                "latency" -> latency = measurement
                "download" -> download = measurement
                "upload" -> upload = measurement
            }
        }
        return MeasurementGroup(
            latency = latency,
            download = download,
            upload = upload,
            submission = null,
            id = groupToken,
        )
    }

    private fun pendingSummary(measurements: Int?, submissions: Int?): PendingSummary {
        if (measurements == null || submissions == null) {
            return PendingSummary(
                summary = "Sync status unknown. Tap refresh.",
                stateKey = MeasurementHistorySyncStateKey.UNKNOWN,
            )
        }
        val total = measurements + submissions
        if (total <= 0) {
            return PendingSummary(
                summary = "All records are synced.",
                stateKey = MeasurementHistorySyncStateKey.SYNCED,
            )
        }
        return PendingSummary(
            summary = "Pending sync queue: $measurements measurement record(s), $submissions submission record(s).",
            stateKey = MeasurementHistorySyncStateKey.PENDING,
        )
    }

    private data class PendingSummary(
        val summary: String,
        val stateKey: MeasurementHistorySyncStateKey,
    )
}
