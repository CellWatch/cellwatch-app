package edu.gatech.cc.cellwatch.androidtestapp

import edu.gatech.cc.cellwatch.androidtestapp.sync.AndroidTestSyncDriver
import edu.gatech.cc.cellwatch.domain.model.FccSubmission
import edu.gatech.cc.cellwatch.domain.model.LatencyData
import edu.gatech.cc.cellwatch.domain.model.Measurement
import edu.gatech.cc.cellwatch.domain.model.MeasurementGroup
import edu.gatech.cc.cellwatch.domain.model.NetworkConnectionType
import edu.gatech.cc.cellwatch.domain.repo.FccSubmissionRepository
import edu.gatech.cc.cellwatch.domain.repo.MeasurementRepository
import edu.gatech.cc.cellwatch.domain.sync.MeasurementSyncService
import edu.gatech.cc.cellwatch.domain.sync.SyncAllReport
import edu.gatech.cc.cellwatch.domain.sync.SyncReport
import edu.gatech.cc.cellwatch.domain.sync.UploadTriggerUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AndroidFailureStatusSmokeTest {

    @Test
    fun phase3FailureSurface_setsExpectedUserVisibleError_whenEnabled() = runBlocking {
        assumeTrue(
            "Set CELLWATCH_RUN_ANDROID_FAILURE_STATUS_SMOKE=1 to enable this Tier2 smoke test",
            System.getenv("CELLWATCH_RUN_ANDROID_FAILURE_STATUS_SMOKE") == "1",
        )

        val useCase = UploadTriggerUseCase(
            syncService = object : MeasurementSyncService {
                override suspend fun syncMeasurements(): SyncReport = SyncReport()
                override suspend fun syncFccSubmissions(): SyncReport = SyncReport()
                override suspend fun syncAll(): SyncAllReport {
                    throw IllegalStateException("synthetic smoke failure")
                }
            },
            measurementRepository = NoOpMeasurementRepository(),
            submissionRepository = NoOpSubmissionRepository(),
        )
        val driver = AndroidTestSyncDriver(useCase)

        val uploadTime = driver.runMeasurementCompleteSync(sampleGroup())

        assertNull(uploadTime)
        assertEquals("synthetic smoke failure", driver.state.value.lastError)
    }
}

private fun sampleGroup(): MeasurementGroup {
    val now = Clock.System.now()
    val measurementId = "smoke-latency-id"
    val groupId = "smoke-group-id"
    return MeasurementGroup(
        latency = Measurement(
            id = measurementId,
            groupId = groupId,
            type = "latency",
            timestamp = now,
            connectionType = NetworkConnectionType.CELLULAR,
            cellularDataEnabled = true,
            latencyData = LatencyData(
                id = "smoke-latency-data-id",
                measurementId = measurementId,
                rtt = 10,
            ),
        ),
        download = null,
        upload = null,
        submission = FccSubmission(id = groupId),
    )
}

private class NoOpMeasurementRepository : MeasurementRepository {
    override suspend fun upsert(measurement: Measurement) = Unit
    override suspend fun delete(id: String) = Unit
    override suspend fun getById(id: String): Measurement? = null
    override suspend fun getByGroupId(groupId: String): List<Measurement> = emptyList()
    override suspend fun getUnsynced(): List<Measurement> = emptyList()
    override suspend fun markUploaded(id: String, uploadedAt: Instant) = Unit
    override fun observeByGroupId(groupId: String): Flow<List<Measurement>> = emptyFlow()
}

private class NoOpSubmissionRepository : FccSubmissionRepository {
    override suspend fun upsert(submission: FccSubmission) = Unit
    override suspend fun delete(id: String) = Unit
    override suspend fun getById(id: String): FccSubmission? = null
    override suspend fun getUnsubmitted(): List<FccSubmission> = emptyList()
    override suspend fun getUnsynced(): List<FccSubmission> = emptyList()
    override suspend fun markUploaded(id: String, uploadedAt: Instant) = Unit
    override fun observeUnsubmitted(): Flow<List<FccSubmission>> = emptyFlow()
}
