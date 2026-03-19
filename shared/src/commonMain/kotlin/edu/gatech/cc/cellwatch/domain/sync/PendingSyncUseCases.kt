package edu.gatech.cc.cellwatch.domain.sync

import edu.gatech.cc.cellwatch.domain.repo.FccSubmissionRepository
import edu.gatech.cc.cellwatch.domain.repo.MeasurementRepository

enum class SyncRunStatus {
    IDLE,
    PENDING,
    IN_PROGRESS,
    PARTIAL_FAILURE,
    FAILED,
    SUCCEEDED,
}

data class PendingSyncCounts(
    val measurements: Int = 0,
    val submissions: Int = 0,
) {
    val total: Int
        get() = measurements + submissions
}

data class SyncRunSummary(
    val status: SyncRunStatus,
    val before: PendingSyncCounts,
    val after: PendingSyncCounts,
    val report: SyncAllReport,
    val userMessage: String,
)

class GetPendingSyncCountsUseCase(
    private val measurementRepository: MeasurementRepository,
    private val submissionRepository: FccSubmissionRepository,
) {
    suspend fun execute(): PendingSyncCounts {
        return PendingSyncCounts(
            measurements = measurementRepository.getUnsynced().size,
            submissions = submissionRepository.getUnsynced().size,
        )
    }
}

class RetryPendingSyncUseCase(
    private val syncService: MeasurementSyncService,
    private val pendingCountsUseCase: GetPendingSyncCountsUseCase,
    private val disabledMessage: String? = null,
) {
    suspend fun execute(): SyncRunSummary {
        val before = pendingCountsUseCase.execute()
        if (disabledMessage != null) {
            return SyncRunSummary(
                status = SyncRunStatus.IDLE,
                before = before,
                after = before,
                report = SyncAllReport(
                    measurements = SyncReport(),
                    submissions = SyncReport(),
                ),
                userMessage = disabledMessage,
            )
        }
        if (before.total == 0) {
            return SyncRunSummary(
                status = SyncRunStatus.IDLE,
                before = before,
                after = before,
                report = SyncAllReport(
                    measurements = SyncReport(),
                    submissions = SyncReport(),
                ),
                userMessage = "No pending uploads.",
            )
        }

        val report = syncService.syncAll()
        val after = pendingCountsUseCase.execute()
        val status = deriveStatus(before = before, after = after, report = report)
        return SyncRunSummary(
            status = status,
            before = before,
            after = after,
            report = report,
            userMessage = renderUserMessage(status = status, before = before, after = after, report = report),
        )
    }

    private fun deriveStatus(
        before: PendingSyncCounts,
        after: PendingSyncCounts,
        report: SyncAllReport,
    ): SyncRunStatus {
        val networkErrors = report.measurements.networkErrors + report.submissions.networkErrors
        val unexpectedErrors = report.measurements.unexpectedErrors + report.submissions.unexpectedErrors
        val totalErrors = networkErrors + unexpectedErrors
        val totalSuccesses = report.measurements.uploaded + report.submissions.uploaded + report.measurements.markedUploaded
        return when {
            totalErrors == 0 && after.total == 0 -> SyncRunStatus.SUCCEEDED
            totalErrors == 0 && after.total > 0 -> SyncRunStatus.PENDING
            totalErrors > 0 && totalSuccesses > 0 -> SyncRunStatus.PARTIAL_FAILURE
            totalErrors > 0 && totalSuccesses == 0 && after.total < before.total -> SyncRunStatus.PARTIAL_FAILURE
            totalErrors > 0 -> SyncRunStatus.FAILED
            else -> SyncRunStatus.PENDING
        }
    }

    private fun renderUserMessage(
        status: SyncRunStatus,
        before: PendingSyncCounts,
        after: PendingSyncCounts,
        report: SyncAllReport,
    ): String {
        val uploaded = report.measurements.uploaded + report.submissions.uploaded
        val marked = report.measurements.markedUploaded
        val networkErrors = report.measurements.networkErrors + report.submissions.networkErrors
        val unexpectedErrors = report.measurements.unexpectedErrors + report.submissions.unexpectedErrors
        val core = "pending before=${before.total}, after=${after.total}, uploaded=$uploaded, marked=$marked, networkErrors=$networkErrors, unexpectedErrors=$unexpectedErrors"
        return when (status) {
            SyncRunStatus.IDLE -> "No pending uploads."
            SyncRunStatus.SUCCEEDED -> "Sync complete. $core"
            SyncRunStatus.PARTIAL_FAILURE -> "Sync partially completed. $core"
            SyncRunStatus.FAILED -> "Sync failed. $core"
            SyncRunStatus.PENDING -> "Sync still pending. $core"
            SyncRunStatus.IN_PROGRESS -> "Sync in progress."
        }
    }
}
