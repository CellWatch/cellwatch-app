package edu.gatech.cc.cellwatch.androidtestapp.sync

import edu.gatech.cc.cellwatch.domain.model.MeasurementGroup
import edu.gatech.cc.cellwatch.domain.repo.FccSubmissionRepository
import edu.gatech.cc.cellwatch.domain.repo.MeasurementRepository
import edu.gatech.cc.cellwatch.domain.sync.MeasurementSyncService
import edu.gatech.cc.cellwatch.domain.sync.SyncAllReport
import edu.gatech.cc.cellwatch.domain.sync.UploadTriggerUseCase
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
    private val useCase = UploadTriggerUseCase(
        syncService = syncService,
        measurementRepository = measurementRepository,
        submissionRepository = submissionRepository,
    )

    suspend fun onMapStartSync(): SyncAllReport = useCase.onMapStart()

    suspend fun onMeasurementCompleteSync(group: MeasurementGroup): Instant? =
        useCase.onMeasurementComplete(group)

    suspend fun resolveUploadTime(group: MeasurementGroup): Instant? =
        useCase.resolveUploadTime(group)
}
