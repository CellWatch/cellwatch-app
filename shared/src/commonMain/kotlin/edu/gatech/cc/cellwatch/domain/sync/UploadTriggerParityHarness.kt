package edu.gatech.cc.cellwatch.domain.sync

import edu.gatech.cc.cellwatch.domain.model.FccSubmission
import edu.gatech.cc.cellwatch.domain.model.LatencyData
import edu.gatech.cc.cellwatch.domain.model.Measurement
import edu.gatech.cc.cellwatch.domain.model.MeasurementGroup
import edu.gatech.cc.cellwatch.domain.model.NetworkConnectionType
import edu.gatech.cc.cellwatch.domain.repo.FccSubmissionRepository
import edu.gatech.cc.cellwatch.domain.repo.MeasurementRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.datetime.Instant

data class UploadTriggerParityResult(
    val measurementsUploaded: Int,
    val measurementsMarkedUploaded: Int,
    val submissionsUploaded: Int,
    val submissionsBlockedBeforeUpload: Boolean,
    val uploadTimeEpochMs: Long?,
)

/**
 * Cross-platform parity scenario runner used by Android and iOS harness tests.
 */
class UploadTriggerParityHarness {

    suspend fun runDefaultScenario(): UploadTriggerParityResult {
        val base = 1_710_000_000_000L
        val measurementTime = Instant.fromEpochMilliseconds(base + 5_000)
        val submissionTime = Instant.fromEpochMilliseconds(base + 9_000)
        val groupId = "parity-group-1"
        val measurementId = "parity-measurement-1"
        val measurement = Measurement(
            id = measurementId,
            groupId = groupId,
            type = "latency",
            timestamp = Instant.fromEpochMilliseconds(base),
            connectionType = NetworkConnectionType.CELLULAR,
            cellularDataEnabled = true,
            uploadTime = measurementTime,
            latencyData = LatencyData(
                id = "parity-latency-1",
                measurementId = measurementId,
                rtt = 24,
                jitter = 2,
                sent = 10,
                received = 10,
            ),
        )
        val submission = FccSubmission(
            id = groupId,
            submitted = false,
            uploadTime = submissionTime,
        )
        val group = MeasurementGroup(
            latency = measurement,
            download = null,
            upload = null,
            submission = submission,
            id = groupId,
        )

        val syncService = FixedSyncService()
        val measurementRepo = InMemoryMeasurementRepository(measurement)
        val submissionRepo = InMemorySubmissionRepository(submission)
        val useCase = UploadTriggerUseCase(
            syncService = syncService,
            measurementRepository = measurementRepo,
            submissionRepository = submissionRepo,
        )

        val mapStart = useCase.onMapStart()
        val uploadTime = useCase.onMeasurementComplete(group)

        return UploadTriggerParityResult(
            measurementsUploaded = mapStart.measurements.uploaded,
            measurementsMarkedUploaded = mapStart.measurements.markedUploaded,
            submissionsUploaded = mapStart.submissions.uploaded,
            submissionsBlockedBeforeUpload = mapStart.submissions.blockedBeforeUpload,
            uploadTimeEpochMs = uploadTime?.toEpochMilliseconds(),
        )
    }
}

private class FixedSyncService : MeasurementSyncService {
    override suspend fun syncMeasurements(): SyncReport = SyncReport(
        attempted = 3,
        uploaded = 1,
        markedUploaded = 1,
        networkErrors = 1,
    )

    override suspend fun syncFccSubmissions(): SyncReport = SyncReport(
        attempted = 2,
        uploaded = 1,
        unexpectedErrors = 1,
        blockedBeforeUpload = true,
    )

    override suspend fun syncAll(): SyncAllReport = SyncAllReport(
        measurements = syncMeasurements(),
        submissions = syncFccSubmissions(),
    )
}

private class InMemoryMeasurementRepository(
    private val measurement: Measurement,
) : MeasurementRepository {
    override suspend fun upsert(measurement: Measurement) = Unit
    override suspend fun delete(id: String) = Unit
    override suspend fun getById(id: String): Measurement? = if (id == measurement.id) measurement else null
    override suspend fun getByGroupId(groupId: String): List<Measurement> =
        if (measurement.groupId == groupId) listOf(measurement) else emptyList()

    override suspend fun getUnsynced(): List<Measurement> = emptyList()
    override suspend fun markUploaded(id: String, uploadedAt: Instant) = Unit
    override fun observeByGroupId(groupId: String): Flow<List<Measurement>> = emptyFlow()
}

private class InMemorySubmissionRepository(
    private val submission: FccSubmission,
) : FccSubmissionRepository {
    override suspend fun upsert(submission: FccSubmission) = Unit
    override suspend fun delete(id: String) = Unit
    override suspend fun getById(id: String): FccSubmission? = if (id == submission.id) submission else null
    override suspend fun getUnsubmitted(): List<FccSubmission> = listOf(submission)
    override suspend fun getUnsynced(): List<FccSubmission> = emptyList()
    override suspend fun markUploaded(id: String, uploadedAt: Instant) = Unit
    override fun observeUnsubmitted(): Flow<List<FccSubmission>> = emptyFlow()
}
