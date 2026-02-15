package edu.gatech.cc.cellwatch.domain.sync

import edu.gatech.cc.cellwatch.domain.model.FccSubmission
import edu.gatech.cc.cellwatch.domain.model.Measurement
import edu.gatech.cc.cellwatch.domain.repo.FccSubmissionRepository
import edu.gatech.cc.cellwatch.domain.repo.MeasurementRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PendingSyncUseCasesTest {
    @Test
    fun getPendingCounts_reportsUnsyncedFromRepositories() = runBlocking {
        val counts = GetPendingSyncCountsUseCase(
            measurementRepository = PendingCountsMeasurementRepository(unsyncedCount = 3),
            submissionRepository = PendingCountsSubmissionRepository(unsyncedCount = 2),
        ).execute()
        assertEquals(3, counts.measurements)
        assertEquals(2, counts.submissions)
        assertEquals(5, counts.total)
    }

    @Test
    fun retryPending_whenNoPending_returnsIdleSummary() = runBlocking {
        val countsUseCase = GetPendingSyncCountsUseCase(
            measurementRepository = PendingCountsMeasurementRepository(unsyncedCount = 0),
            submissionRepository = PendingCountsSubmissionRepository(unsyncedCount = 0),
        )
        val summary = RetryPendingSyncUseCase(
            syncService = FakeSyncService(),
            pendingCountsUseCase = countsUseCase,
        ).execute()
        assertEquals(SyncRunStatus.IDLE, summary.status)
        assertEquals(0, summary.before.total)
        assertEquals(0, summary.after.total)
    }

    @Test
    fun retryPending_successfulSync_returnsSucceeded() = runBlocking {
        val measurementRepo = PendingCountsMeasurementRepository(unsyncedCount = 2)
        val submissionRepo = PendingCountsSubmissionRepository(unsyncedCount = 1)
        val countsUseCase = GetPendingSyncCountsUseCase(measurementRepo, submissionRepo)
        val summary = RetryPendingSyncUseCase(
            syncService = FakeSyncService(
                report = SyncAllReport(
                    measurements = SyncReport(attempted = 2, uploaded = 2),
                    submissions = SyncReport(attempted = 1, uploaded = 1),
                ),
                afterSync = {
                    measurementRepo.unsyncedCount = 0
                    submissionRepo.unsyncedCount = 0
                },
            ),
            pendingCountsUseCase = countsUseCase,
        ).execute()

        assertEquals(SyncRunStatus.SUCCEEDED, summary.status)
        assertEquals(3, summary.before.total)
        assertEquals(0, summary.after.total)
        assertTrue(summary.userMessage.contains("Sync complete."))
    }

    @Test
    fun retryPending_partialFailure_returnsPartialFailure() = runBlocking {
        val measurementRepo = PendingCountsMeasurementRepository(unsyncedCount = 3)
        val submissionRepo = PendingCountsSubmissionRepository(unsyncedCount = 1)
        val countsUseCase = GetPendingSyncCountsUseCase(measurementRepo, submissionRepo)
        val summary = RetryPendingSyncUseCase(
            syncService = FakeSyncService(
                report = SyncAllReport(
                    measurements = SyncReport(attempted = 3, uploaded = 1, networkErrors = 2),
                    submissions = SyncReport(attempted = 1, uploaded = 1),
                ),
                afterSync = {
                    measurementRepo.unsyncedCount = 2
                    submissionRepo.unsyncedCount = 0
                },
            ),
            pendingCountsUseCase = countsUseCase,
        ).execute()

        assertEquals(SyncRunStatus.PARTIAL_FAILURE, summary.status)
        assertEquals(4, summary.before.total)
        assertEquals(2, summary.after.total)
    }

    @Test
    fun retryPending_failedSync_returnsFailed() = runBlocking {
        val measurementRepo = PendingCountsMeasurementRepository(unsyncedCount = 2)
        val submissionRepo = PendingCountsSubmissionRepository(unsyncedCount = 1)
        val countsUseCase = GetPendingSyncCountsUseCase(measurementRepo, submissionRepo)
        val summary = RetryPendingSyncUseCase(
            syncService = FakeSyncService(
                report = SyncAllReport(
                    measurements = SyncReport(attempted = 2, networkErrors = 2),
                    submissions = SyncReport(attempted = 1, unexpectedErrors = 1),
                ),
                afterSync = {
                    measurementRepo.unsyncedCount = 2
                    submissionRepo.unsyncedCount = 1
                },
            ),
            pendingCountsUseCase = countsUseCase,
        ).execute()

        assertEquals(SyncRunStatus.FAILED, summary.status)
        assertEquals(3, summary.before.total)
        assertEquals(3, summary.after.total)
        assertTrue(summary.userMessage.contains("Sync failed."))
    }
}

private class FakeSyncService(
    private val report: SyncAllReport = SyncAllReport(
        measurements = SyncReport(),
        submissions = SyncReport(),
    ),
    private val afterSync: () -> Unit = {},
) : MeasurementSyncService {
    override suspend fun syncMeasurements(): SyncReport = report.measurements

    override suspend fun syncFccSubmissions(): SyncReport = report.submissions

    override suspend fun syncAll(): SyncAllReport {
        afterSync()
        return report
    }
}

private class PendingCountsMeasurementRepository(
    var unsyncedCount: Int,
) : MeasurementRepository {
    override suspend fun upsert(measurement: Measurement) = Unit

    override suspend fun delete(id: String) = Unit

    override suspend fun getById(id: String): Measurement? = null

    override suspend fun getByGroupId(groupId: String): List<Measurement> = emptyList()

    override suspend fun getUnsynced(): List<Measurement> = List(unsyncedCount) { index ->
        Measurement(id = "m-$index", groupId = "g", type = "latency")
    }

    override suspend fun markUploaded(id: String, uploadedAt: Instant) = Unit

    override fun observeByGroupId(groupId: String): Flow<List<Measurement>> = emptyFlow()
}

private class PendingCountsSubmissionRepository(
    var unsyncedCount: Int,
) : FccSubmissionRepository {
    override suspend fun upsert(submission: FccSubmission) = Unit

    override suspend fun delete(id: String) = Unit

    override suspend fun getById(id: String): FccSubmission? = null

    override suspend fun getUnsubmitted(): List<FccSubmission> = emptyList()

    override suspend fun getUnsynced(): List<FccSubmission> = List(unsyncedCount) { index ->
        FccSubmission(id = "s-$index")
    }

    override suspend fun markUploaded(id: String, uploadedAt: Instant) = Unit

    override fun observeUnsubmitted(): Flow<List<FccSubmission>> = emptyFlow()
}
