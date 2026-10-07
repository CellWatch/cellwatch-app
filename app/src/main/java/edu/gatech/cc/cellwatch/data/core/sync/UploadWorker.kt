package edu.gatech.cc.cellwatch.data.core.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import edu.gatech.cc.cellwatch.CellWatchApp

/**
 * Uploads pending measurements and submissions in the background, so closing a screen doesn't
 * cancel an upload part way through.
 */
class UploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val summary = CellWatchApp.measurementRepository.uploadPending()
        return if (summary.failed > 0 && runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()
    }

    companion object {
        private const val WORK_NAME = "upload-pending"
        private const val MAX_ATTEMPTS = 5

        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<UploadWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()

            // Queue behind an upload that's already running instead of replacing it, so it's never
            // cancelled part way through and anything saved meanwhile is picked up by the next run.
            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }
    }
}
