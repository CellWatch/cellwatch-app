package edu.gatech.cc.cellwatch.data.core.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import edu.gatech.cc.cellwatch.CellWatchApp
import java.util.concurrent.TimeUnit

/**
 * Uploads pending measurements and submissions in the background, so closing a screen doesn't
 * cancel an upload part way through. Runs after each test, when the app comes to the foreground,
 * and every hour while the phone has a connection, so results saved without service still upload
 * even if the app isn't opened again.
 */
class UploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val summary = CellWatchApp.measurementRepository.uploadPending()
        return if (summary.failed > 0 && runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()
    }

    companion object {
        private const val WORK_NAME = "upload-pending"
        private const val PERIODIC_WORK_NAME = "upload-pending-periodic"
        private const val MAX_ATTEMPTS = 5

        /**
         * By default, queues behind an upload that's already running instead of replacing it, so
         * it's never cancelled part way through and anything saved meanwhile is picked up by the
         * next run. When nothing new was saved, [ExistingWorkPolicy.KEEP] settles for an upload
         * that's already queued or running.
         */
        fun enqueue(context: Context, policy: ExistingWorkPolicy = ExistingWorkPolicy.APPEND_OR_REPLACE) {
            val request = OneTimeWorkRequestBuilder<UploadWorker>()
                .setConstraints(whenConnected())
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, policy, request)
        }

        /**
         * Keeps the hourly upload that's already scheduled rather than replacing it, so calling
         * this at every launch doesn't push the next run back. Phones that already have it keep
         * its old settings, so changing [periodicRequest] needs [ExistingPeriodicWorkPolicy.UPDATE].
         */
        fun schedulePeriodic(context: Context) {
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(PERIODIC_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, periodicRequest())
        }

        internal fun periodicRequest(): PeriodicWorkRequest =
            PeriodicWorkRequestBuilder<UploadWorker>(1, TimeUnit.HOURS)
                .setConstraints(whenConnected())
                .build()

        private fun whenConnected() =
            Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
    }
}
