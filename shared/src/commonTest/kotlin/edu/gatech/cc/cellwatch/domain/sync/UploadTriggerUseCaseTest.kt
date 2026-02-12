package edu.gatech.cc.cellwatch.domain.sync

import edu.gatech.cc.cellwatch.domain.model.FccSubmission
import edu.gatech.cc.cellwatch.domain.model.LatencyData
import edu.gatech.cc.cellwatch.domain.model.Measurement
import edu.gatech.cc.cellwatch.domain.model.MeasurementGroup
import edu.gatech.cc.cellwatch.domain.model.UploadDownloadData
import edu.gatech.cc.cellwatch.domain.repo.FccSubmissionRepository
import edu.gatech.cc.cellwatch.domain.repo.MeasurementRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UploadTriggerUseCaseTest {

    @Test
    fun onMapStart_delegatesToSyncAll() = runBlocking {
        val syncService = RecordingSyncService(
            report = SyncAllReport(
                measurements = SyncReport(attempted = 2, uploaded = 2),
                submissions = SyncReport(attempted = 1, uploaded = 1),
            ),
        )
        val useCase = UploadTriggerUseCase(
            syncService = syncService,
            measurementRepository = FakeMeasurementRepository(),
            submissionRepository = FakeFccSubmissionRepository(),
        )

        val report = useCase.onMapStart()

        assertEquals(1, syncService.syncAllCalls)
        assertEquals(2, report.measurements.uploaded)
        assertEquals(1, report.submissions.uploaded)
    }

    @Test
    fun onMeasurementComplete_syncsThenReturnsLatestUploadTime() = runBlocking {
        val measurementTime = Instant.fromEpochMilliseconds(1_710_000_001_000L)
        val submissionTime = Instant.fromEpochMilliseconds(1_710_000_009_000L)
        val syncService = RecordingSyncService()
        val useCase = UploadTriggerUseCase(
            syncService = syncService,
            measurementRepository = FakeMeasurementRepository(
                byId = mapOf(
                    "m-latency" to latency("group-1", "m-latency", measurementTime),
                ),
            ),
            submissionRepository = FakeFccSubmissionRepository(
                byId = mapOf(
                    "group-1" to FccSubmission(id = "group-1", uploadTime = submissionTime),
                ),
            ),
        )

        val uploadTime = useCase.onMeasurementComplete(
            group = MeasurementGroup(
                latency = latency("group-1", "m-latency", null),
                download = null,
                upload = null,
                submission = FccSubmission(id = "group-1"),
            ),
        )

        assertEquals(1, syncService.syncAllCalls)
        assertEquals(submissionTime, uploadTime)
    }

    @Test
    fun resolveUploadTime_returnsNullWhenEitherSidePending() = runBlocking {
        val measurementTime = Instant.fromEpochMilliseconds(1_710_000_001_000L)
        val useCase = UploadTriggerUseCase(
            syncService = RecordingSyncService(),
            measurementRepository = FakeMeasurementRepository(
                byId = mapOf("m-latency" to latency("group-1", "m-latency", measurementTime)),
            ),
            submissionRepository = FakeFccSubmissionRepository(
                byId = mapOf("group-1" to FccSubmission(id = "group-1", uploadTime = null)),
            ),
        )

        val uploadTime = useCase.resolveUploadTime(
            MeasurementGroup(
                latency = latency("group-1", "m-latency", null),
                download = null,
                upload = null,
                submission = FccSubmission(id = "group-1"),
            ),
        )

        assertNull(uploadTime)
    }

    @Test
    fun resolveUploadTime_fallsBackToDownloadThenUploadMeasurementIds() = runBlocking {
        val downloadTime = Instant.fromEpochMilliseconds(1_710_000_002_000L)
        val uploadTime = Instant.fromEpochMilliseconds(1_710_000_003_000L)
        val useCase = UploadTriggerUseCase(
            syncService = RecordingSyncService(),
            measurementRepository = FakeMeasurementRepository(
                byId = mapOf(
                    "m-download" to download("group-2", "m-download", downloadTime),
                    "m-upload" to upload("group-3", "m-upload", uploadTime),
                ),
            ),
            submissionRepository = FakeFccSubmissionRepository(),
        )

        val fromDownload = useCase.resolveUploadTime(
            MeasurementGroup(
                latency = null,
                download = download("group-2", "m-download", null),
                upload = null,
                submission = null,
            ),
        )
        val fromUpload = useCase.resolveUploadTime(
            MeasurementGroup(
                latency = null,
                download = null,
                upload = upload("group-3", "m-upload", null),
                submission = null,
            ),
        )

        assertEquals(downloadTime, fromDownload)
        assertEquals(uploadTime, fromUpload)
    }
}

private class RecordingSyncService(
    val report: SyncAllReport = SyncAllReport(SyncReport(), SyncReport()),
) : MeasurementSyncService {
    var syncAllCalls: Int = 0

    override suspend fun syncMeasurements(): SyncReport = report.measurements

    override suspend fun syncFccSubmissions(): SyncReport = report.submissions

    override suspend fun syncAll(): SyncAllReport {
        syncAllCalls += 1
        return report
    }
}

private class FakeMeasurementRepository(
    private val byId: Map<String, Measurement> = emptyMap(),
) : MeasurementRepository {
    override suspend fun upsert(measurement: Measurement) = Unit

    override suspend fun delete(id: String) = Unit

    override suspend fun getById(id: String): Measurement? = byId[id]

    override suspend fun getByGroupId(groupId: String): List<Measurement> = emptyList()

    override suspend fun getUnsynced(): List<Measurement> = emptyList()

    override suspend fun markUploaded(id: String, uploadedAt: Instant) = Unit

    override fun observeByGroupId(groupId: String): Flow<List<Measurement>> = emptyFlow()
}

private class FakeFccSubmissionRepository(
    private val byId: Map<String, FccSubmission> = emptyMap(),
) : FccSubmissionRepository {
    override suspend fun upsert(submission: FccSubmission) = Unit

    override suspend fun delete(id: String) = Unit

    override suspend fun getById(id: String): FccSubmission? = byId[id]

    override suspend fun getUnsubmitted(): List<FccSubmission> = emptyList()

    override suspend fun getUnsynced(): List<FccSubmission> = emptyList()

    override suspend fun markUploaded(id: String, uploadedAt: Instant) = Unit

    override fun observeUnsubmitted(): Flow<List<FccSubmission>> = emptyFlow()
}

private fun latency(groupId: String, id: String, uploadTime: Instant?): Measurement = Measurement(
    id = id,
    groupId = groupId,
    type = "latency",
    uploadTime = uploadTime,
    latencyData = LatencyData(id = "latency-$id", measurementId = id, rtt = 10),
)

private fun download(groupId: String, id: String, uploadTime: Instant?): Measurement = Measurement(
    id = id,
    groupId = groupId,
    type = "download",
    uploadTime = uploadTime,
    uploadDownloadData = UploadDownloadData(id = "download-$id", measurementId = id, bytes = 100),
)

private fun upload(groupId: String, id: String, uploadTime: Instant?): Measurement = Measurement(
    id = id,
    groupId = groupId,
    type = "upload",
    uploadTime = uploadTime,
    uploadDownloadData = UploadDownloadData(id = "upload-$id", measurementId = id, bytes = 100),
)
