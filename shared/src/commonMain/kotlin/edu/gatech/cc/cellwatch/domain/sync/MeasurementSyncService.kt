package edu.gatech.cc.cellwatch.domain.sync

data class SyncAllReport(
    val measurements: SyncReport,
    val submissions: SyncReport,
)

fun SyncAllReport.renderForStatus(): String {
    return buildString {
        appendLine(
            "measurements: attempted=${measurements.attempted}, uploaded=${measurements.uploaded}, " +
                "markedUploaded=${measurements.markedUploaded}, networkErrors=${measurements.networkErrors}, " +
                "unexpectedErrors=${measurements.unexpectedErrors}",
        )
        appendLine(measurements.errorSummary.renderForStatus())
        appendLine(
            "submissions: attempted=${submissions.attempted}, uploaded=${submissions.uploaded}, " +
                "blockedBeforeUpload=${submissions.blockedBeforeUpload}, networkErrors=${submissions.networkErrors}, " +
                "unexpectedErrors=${submissions.unexpectedErrors}",
        )
        append(submissions.errorSummary.renderForStatus())
    }
}

interface MeasurementSyncService {
    suspend fun syncMeasurements(): SyncReport
    suspend fun syncFccSubmissions(): SyncReport
    suspend fun syncAll(): SyncAllReport
}

object NoOpMeasurementSyncService : MeasurementSyncService {
    override suspend fun syncMeasurements(): SyncReport = SyncReport()

    override suspend fun syncFccSubmissions(): SyncReport = SyncReport()

    override suspend fun syncAll(): SyncAllReport =
        SyncAllReport(
            measurements = SyncReport(),
            submissions = SyncReport(),
        )
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
