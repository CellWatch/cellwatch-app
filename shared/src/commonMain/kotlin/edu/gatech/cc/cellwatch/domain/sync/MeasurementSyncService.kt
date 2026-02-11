package edu.gatech.cc.cellwatch.domain.sync

data class SyncAllReport(
    val measurements: SyncReport,
    val submissions: SyncReport,
)

interface MeasurementSyncService {
    suspend fun syncMeasurements(): SyncReport
    suspend fun syncFccSubmissions(): SyncReport
    suspend fun syncAll(): SyncAllReport
}

class DefaultMeasurementSyncService(
    private val useCase: MeasurementSyncUseCase,
) : MeasurementSyncService {
    override suspend fun syncMeasurements(): SyncReport = useCase.syncMeasurements()

    override suspend fun syncFccSubmissions(): SyncReport = useCase.syncFccSubmissions()

    override suspend fun syncAll(): SyncAllReport =
        SyncAllReport(
            measurements = syncMeasurements(),
            submissions = syncFccSubmissions(),
        )
}
