package edu.gatech.cc.cellwatch.domain.sync

import edu.gatech.cc.cellwatch.domain.fcc.MeasurementSequenceOrchestrator
import edu.gatech.cc.cellwatch.domain.fcc.MeasurementSequenceOutcome
import edu.gatech.cc.cellwatch.domain.fcc.MeasurementSequenceRequest
import kotlinx.datetime.Instant

data class MeasurementSequenceSyncOutcome(
    val sequenceOutcome: MeasurementSequenceOutcome,
    val mapStartReport: SyncAllReport,
    val measurementCompleteUploadTime: Instant?,
)

/**
 * Canonical shared flow for harnesses:
 * 1) run map-start sync
 * 2) run full Phase 3 MSAK measurement sequence
 * 3) run measurement-complete sync for that sequence result
 */
class MeasurementSequenceSyncOrchestrator(
    private val sequenceOrchestrator: MeasurementSequenceOrchestrator,
    private val uploadTriggerUseCase: UploadTriggerUseCase,
) {
    suspend fun run(request: MeasurementSequenceRequest): MeasurementSequenceSyncOutcome {
        val mapStartReport = uploadTriggerUseCase.onMapStart()
        val sequenceOutcome = sequenceOrchestrator.run(request)
        val uploadTime = uploadTriggerUseCase.onMeasurementComplete(sequenceOutcome.group)
        return MeasurementSequenceSyncOutcome(
            sequenceOutcome = sequenceOutcome,
            mapStartReport = mapStartReport,
            measurementCompleteUploadTime = uploadTime,
        )
    }
}
