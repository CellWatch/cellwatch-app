package edu.gatech.cc.cellwatch.domain.sync

import edu.gatech.cc.cellwatch.domain.model.FccSubmission
import edu.gatech.cc.cellwatch.domain.model.Measurement
import edu.gatech.cc.cellwatch.domain.repo.FccSubmissionRepository
import edu.gatech.cc.cellwatch.domain.repo.MeasurementRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue

object PendingSyncParityContract {
    fun assertPendingRetryLifecycle() = runBlocking {
        val measurementRepo = PendingCountsMeasurementRepository(unsyncedCount = 2)
        val submissionRepo = PendingCountsSubmissionRepository(unsyncedCount = 1)
        val countsUseCase = GetPendingSyncCountsUseCase(measurementRepo, submissionRepo)

        val before = countsUseCase.execute()
        assertEquals(2, before.measurements)
        assertEquals(1, before.submissions)
        assertEquals(3, before.total)

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

    private class FakeSyncService(
        private val report: SyncAllReport,
        private val afterSync: () -> Unit,
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
}
