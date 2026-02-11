package edu.gatech.cc.cellwatch.domain.sync

import edu.gatech.cc.cellwatch.domain.model.MeasurementGroup
import edu.gatech.cc.cellwatch.domain.repo.FccSubmissionRepository
import edu.gatech.cc.cellwatch.domain.repo.MeasurementRepository
import kotlinx.datetime.Instant

/**
 * Shared-first extraction of legacy upload trigger orchestration:
 * - map-start: trigger sync for unsynced records
 * - measurement-complete: trigger sync and resolve displayed upload time
 */
class UploadTriggerUseCase(
    private val syncService: MeasurementSyncService,
    private val measurementRepository: MeasurementRepository,
    private val submissionRepository: FccSubmissionRepository,
) {
    suspend fun onMapStart(): SyncAllReport = syncService.syncAll()

    suspend fun onMeasurementComplete(group: MeasurementGroup): Instant? {
        syncService.syncAll()
        return resolveUploadTime(group)
    }

    suspend fun resolveUploadTime(group: MeasurementGroup): Instant? {
        val measurementId = group.latency?.id ?: group.download?.id ?: group.upload?.id
        val submissionId = group.submission?.id

        val measurementTime = measurementId
            ?.let { id -> measurementRepository.getById(id)?.uploadTime }
        val submissionTime = submissionId
            ?.let { id -> submissionRepository.getById(id)?.uploadTime }

        if (measurementId == null) return submissionTime
        if (submissionId == null) return measurementTime
        if (measurementTime == null || submissionTime == null) return null
        return if (measurementTime.toEpochMilliseconds() >= submissionTime.toEpochMilliseconds()) {
            measurementTime
        } else {
            submissionTime
        }
    }
}
