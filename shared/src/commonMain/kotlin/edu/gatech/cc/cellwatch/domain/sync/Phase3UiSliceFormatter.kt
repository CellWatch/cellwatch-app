package edu.gatech.cc.cellwatch.domain.sync

data class Phase3UiSliceResult(
    val groupId: String,
    val throughputMachine: String,
    val latencyMachine: String,
    val submissionCreated: Boolean,
    val mapStartMeasurementsUploaded: Int,
    val mapStartSubmissionsUploaded: Int,
    val measurementCompleteUploadTimeSet: Boolean,
    val persistedMeasurements: Int,
    val persistedSubmissions: Int,
    val capabilityPersistenceSummary: String,
    val capabilitySummary: String,
)

object Phase3UiSliceFormatter {
    fun format(
        envelopeText: String,
        result: Phase3UiSliceResult,
    ): String {
        return buildString {
            appendLine(envelopeText)
            appendLine("group=${result.groupId}")
            appendLine("throughput=${result.throughputMachine}")
            appendLine("latency=${result.latencyMachine}")
            appendLine("submissionCreated=${result.submissionCreated}")
            appendLine(
                "mapStartUploaded(m=${result.mapStartMeasurementsUploaded}," +
                    "s=${result.mapStartSubmissionsUploaded})"
            )
            appendLine("measurementCompleteUploadTimeSet=${result.measurementCompleteUploadTimeSet}")
            appendLine(
                "persistedMeasurements=${result.persistedMeasurements}, " +
                    "persistedSubmissions=${result.persistedSubmissions}"
            )
            appendLine(result.capabilityPersistenceSummary)
            append(result.capabilitySummary)
        }
    }
}
