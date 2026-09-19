package edu.gatech.cc.cellwatch.domain.sync

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job

/**
 * Handle on a running measurement, so a caller can stop it.
 *
 * Exists because a measurement interrupted by the user backgrounding the app
 * must be cancelled outright rather than left to produce partial data. iOS has
 * no foreground-service equivalent, so the app cannot keep running; abandoning
 * the run cleanly is the honest alternative.
 *
 * Common rather than iOS-only: Android reaches the same conclusion by a
 * different route. Rather than add a foreground service, both platforms
 * require the app to stay open, so both need to cancel the same way.
 */
class MeasurementRunHandle internal constructor(private val job: Job) {
    val isRunning: Boolean get() = job.isActive

    fun cancel() {
        job.cancel(CancellationException(CANCELLATION_REASON))
    }

    companion object {
        const val CANCELLATION_REASON = "measurement cancelled: app left the foreground"
    }
}
