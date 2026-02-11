package edu.gatech.cc.cellwatch.androidtestapp.sync

import edu.gatech.cc.cellwatch.domain.model.MeasurementGroup
import edu.gatech.cc.cellwatch.domain.repo.FccSubmissionRepository
import edu.gatech.cc.cellwatch.domain.repo.MeasurementRepository
import edu.gatech.cc.cellwatch.domain.sync.MeasurementSyncService
import edu.gatech.cc.cellwatch.domain.sync.SyncAllReport
import kotlinx.datetime.Instant

/**
 * Isolated bridge that mirrors legacy Android UI upload triggers:
 * - map start -> sync unsynced records
 * - measurement completion -> sync and resolve group upload time
 */
class LegacySharedSyncFlow(
    private val syncService: MeasurementSyncService,
    private val measurementRepository: MeasurementRepository,
    private val submissionRepository: FccSubmissionRepository,
) {
    suspend fun onMapStartSync(): SyncAllReport = syncService.syncAll()

    suspend fun onMeasurementCompleteSync(group: MeasurementGroup): Instant? {
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
