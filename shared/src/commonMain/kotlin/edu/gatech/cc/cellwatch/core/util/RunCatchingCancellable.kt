package edu.gatech.cc.cellwatch.core.util

import kotlin.coroutines.cancellation.CancellationException

/**
 * [runCatching] that does not swallow coroutine cancellation.
 *
 * `runCatching` catches [Throwable], and in a coroutine that includes
 * [CancellationException] - the signal the framework uses to unwind a cancelled
 * job. Catching it turns "the caller asked us to stop" into either a reported
 * failure or, worse, a swallowed one that lets the coroutine carry on running
 * work nobody is waiting for.
 *
 * This matters most around `msak-client-kmp`'s suspend APIs. Before 0.3.0,
 * `runLatency`/`runThroughput` passed a `SupervisorJob()` to `withContext`,
 * which reparented the measurement and silently detached it from the caller's
 * cancellation, so cancellation rarely reached these call sites at all. 0.3.0
 * removed that, and cancellation now propagates properly.
 */
internal inline fun <R> runCatchingCancellable(block: () -> R): Result<R> {
    return try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        Result.failure(t)
    }
}
