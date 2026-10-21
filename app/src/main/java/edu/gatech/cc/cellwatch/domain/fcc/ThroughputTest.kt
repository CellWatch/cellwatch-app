package edu.gatech.cc.cellwatch.domain.fcc

import android.os.Handler
import android.os.Looper
import edu.gatech.cc.cellwatch.CellWatchApp
import edu.gatech.cc.cellwatch.core.util.Log
import edu.gatech.cc.cellwatch.msak.Server
import edu.gatech.cc.cellwatch.msak.throughput.ThroughputDirection
import edu.gatech.cc.cellwatch.msak.throughput.ThroughputStream
import edu.gatech.cc.cellwatch.msak.throughput.ThroughputTest
import edu.gatech.cc.cellwatch.msak.throughput.ThroughputUpdate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class ThroughputTest(
    private val server: Server,
    private val streams: Int,
    private val direction: ThroughputDirection,
    groupId: String,
    measurementId: String? = null,
    private val maxWarmupTime: Long = MAX_WARMUP_MILLIS,
    private val maxActiveTime: Long = MAX_ACTIVE_MILLIS,
): MeasurementTest<ThroughputResult>(
    groupId,
    if (direction == ThroughputDirection.DOWNLOAD) "download" else "upload",
) {
    private val TAG = this::class.simpleName
    val msakTest = ThroughputTest(
        server,
        direction,
        streams,
        maxWarmupTime + maxActiveTime,
        0,
        measurementId,
        userAgent = CellWatchApp.userAgent,
    )
    private var activeStartTime: Instant? = null
    private val readyForActive = MutableList(streams) { false }
    private var lastWarmupUpdates: List<ThroughputUpdate>? = null
    private var error: Throwable? = null
    private val _metricsChan = Channel<ThroughputMetrics>(32)
    private val latestUpdates; get() = msakTest.streams.map {s ->
        // manually iterate by index to avoid ConcurrentModificationException
        var latest: ThroughputUpdate? = null
        for (i in (s.updates.size - 1) downTo 0) {
            // sometimes we get an index out of bounds exception here (not sure how) -- avoid it
            val update = s.updates.getOrNull(i) ?: continue
            if (isFromReceiver(update)) {
                latest = update
                break
            }
        }
        latest
    }
    private val handler = Handler(Looper.getMainLooper())
    private val latestMetrics: ThroughputMetrics?
        get() {
            val startTime = activeStartTime ?: msakTest.startTime ?: return null
            val endTime = msakTest.endTime ?: Clock.System.now()
            val usecs = (endTime - startTime).inWholeMicroseconds
            return calcAggregateMetrics(usecs, latestUpdates, lastWarmupUpdates)
        }

    val metricsChan: ReceiveChannel<ThroughputMetrics> = _metricsChan

    override suspend fun measure(): ThroughputResult {
        try {
            if (server is UnreachableServer) {
                return unreachableResult(server.machine, Clock.System.now())
            }

            msakTest.start()
            handler.postDelayed({ catchErrors { startActive() } }, maxWarmupTime)
            msakTest.updatesChan.consumeEach { handleUpdate(it) }

            return summarize(
                serverHost = msakTest.serverHost,
                start = msakTest.startTime!!, // assume set since we started the test above
                activeStart = activeStartTime,
                end = msakTest.endTime,
                latest = latestUpdates,
                lastWarmupUpdates = lastWarmupUpdates,
                error = error,
                streamErrors = msakTest.streams.map { it.error },
            )
        } catch (c: CancellationException) {
            Log.i(TAG, "throughput test cancelled")
            msakTest.stop()
            throw c
        } catch (e: Throwable) {
            Log.e(TAG, "unexpected error running throughput test", e)
            msakTest.stop()
            throw e
        } finally {
            handler.removeCallbacksAndMessages(null)
            _metricsChan.close()
        }
    }

    private fun handleUpdate(update: ThroughputUpdate) {
        if (!isFromReceiver(update)) {
            return
        }

        if (activeStartTime == null && shouldStartActive()) {
            startActive()
        }

        val latest = latestMetrics
        if (latest != null) {
            val result = _metricsChan.trySend(latest)
            if (!result.isSuccess) {
                Log.d(TAG, "failed to send throughput update on channel: $result")
            }

            if (activeStartTime != null && latest.bytes >= 10e9) {
                msakTest.stop()
            }
        }
    }

    private fun startActive() {
        if (activeStartTime != null) {
            return
        }

        activeStartTime = Clock.System.now()
        handler.postDelayed({ catchErrors { msakTest.stop() } }, maxActiveTime)

        try {
            lastWarmupUpdates = msakTest.streams.mapIndexed { i, stream ->
                // avoid using an iterator on the list of updates to prevent a ConcurrentModificationException
                // see https://stackoverflow.com/questions/27818867/java-concurrentmodificationexception-when-iterating-arraylist
                var update: ThroughputUpdate? = null
                for (j in (stream.updates.size - 1) downTo 0) {
                    val u = stream.updates[j]
                    if (isFromReceiver(u)) {
                        update = u
                        break
                    }
                }

                update ?: throw NoSuchElementException("stream $i has no warmup updates")
            }
        } catch (e: NoSuchElementException) {
            Log.i(TAG, "one or more streams had no updates during the warmup period", e)
            error = MissingWarmupUpdateException()
            msakTest.stop()
        }
    }

    private fun shouldStartActive(): Boolean {
        for (stream in 0 until streams) {
            if (readyForActive[stream]) {
                continue
            }

            if (isReadyForActive(stream)) {
                Log.d(TAG, "stream $stream ready for active")
                readyForActive[stream] = true
            }
        }

        return readyForActive.all { it }
    }

    private fun isReadyForActive(stream: Int): Boolean {
        // avoid using an iterator on the list of updates to prevent a ConcurrentModificationException
        // see https://stackoverflow.com/questions/27818867/java-concurrentmodificationexception-when-iterating-arraylist
        val updates = ArrayList<ThroughputUpdate>()
        for (i in 0 until msakTest.streams[stream].updates.size) {
            val update = msakTest.streams[stream].updates[i]
            if (isFromReceiver(update)) {
                updates.add(update)
            }
        }

        if (updates.size < 2) {
            return false
        }

        val curBPS = calcBytesPerSec(updates[updates.size - 1])
        val lastBPS = calcBytesPerSec(updates[updates.size - 2])

        return curBPS > 0 && lastBPS > 0 && curBPS <= lastBPS
    }

    private fun isFromReceiver(update: ThroughputUpdate): Boolean {
        return update.fromServer == (direction == ThroughputDirection.UPLOAD)
    }

    private fun catchErrors(fn: () -> Unit) {
        try {
            fn()
        } catch (e: Throwable) {
            Log.e(TAG, "unexpected error running throughput test", e)
            error = e
        }
    }

    internal class MissingWarmupUpdateException: Exception("one or more streams had no updates in warmup period")

    companion object {
        /** The warmup ends early once every stream's rate stops rising, but never runs longer than this. */
        const val MAX_WARMUP_MILLIS = 10000L

        /** How long the active (measured) part of the test runs after the warmup. */
        const val MAX_ACTIVE_MILLIS = 10000L

        /** The result recorded when no throughput server could be located. */
        internal fun unreachableResult(host: String, now: Instant) = ThroughputResult(
            host,
            false,
            now,
            ThroughputMetrics(0, 0, 0.0),
            ThroughputMetrics(0, 0, 0.0),
        )

        /**
         * Builds the result of a finished msak throughput test from the receiver's updates and the
         * errors the test and its streams ended with. Kept free of Android and msak state so it can
         * be unit tested.
         *
         * @param latest each stream's latest update from the receiver, or null if it had none
         * @param lastWarmupUpdates each stream's last receiver update before the active period
         *   started, or null if the active period never started or a stream had none
         * @param error an error the test itself ended with (as opposed to one of its streams)
         * @param streamErrors the error each stream ended with, if any
         */
        internal fun summarize(
            serverHost: String,
            start: Instant,
            activeStart: Instant?,
            end: Instant?,
            latest: List<ThroughputUpdate?>,
            lastWarmupUpdates: List<ThroughputUpdate>?,
            error: Throwable?,
            streamErrors: List<Throwable?>,
            now: () -> Instant = { Clock.System.now() },
        ): ThroughputResult {
            val warmupMetrics = calcAggregateMetrics(
                ((activeStart ?: now()) - start).inWholeMicroseconds,
                lastWarmupUpdates ?: latest,
            )

            val haveActiveUpdates = latest.all { it != null && it != lastWarmupUpdates?.get(it.stream) }
            val activeMetrics = if (activeStart != null && haveActiveUpdates) {
                calcAggregateMetrics(
                    ((end ?: now()) - activeStart).inWholeMicroseconds,
                    latest,
                    lastWarmupUpdates,
                )
            } else ThroughputMetrics(0, 0, 0.0)

            // According to the FCC, a test is successful if it transmitted >0 bytes of data, even
            // if it ends prematurely, for example because of a network error. Throw non-network
            // related errors; these indicate that something went wrong that invalidates the test.
            val success = when(error) {
                null -> true
                is MissingWarmupUpdateException -> false
                else -> throw error
            } && streamErrors.all {
                when (it) {
                    null -> true
                    is ThroughputStream.UploadDataException,
                    is ThroughputStream.UnexpectedCloseException,
                    is ThroughputStream.FailureException -> true
                    else -> throw it
                }
            } && activeMetrics.bytesPerSec > 0

            return ThroughputResult(serverHost, success, start, warmupMetrics, activeMetrics)
        }

        private fun calcAggregateMetrics(
            usecs: Long,
            lastUpdates: List<ThroughputUpdate?>,
            firstUpdates: List<ThroughputUpdate>? = null,
        ): ThroughputMetrics {
            val streamMetrics = lastUpdates.mapIndexed { i, lastUpdate ->
                val firstUpdate = firstUpdates?.getOrNull(i)

                if (lastUpdate == null) {
                    ThroughputMetrics(0, 0, 0.0)
                } else if (firstUpdate == null) {
                    val bytes = getBytesReceived(lastUpdate)
                    ThroughputMetrics(bytes, usecs, calcBytesPerSec(bytes, lastUpdate.measurement.ElapsedTime))
                } else {
                    val lastBytes = getBytesReceived(lastUpdate)
                    val firstBytes = getBytesReceived(firstUpdate)
                    val bytes = lastBytes - firstBytes
                    val elapsedUsecs = lastUpdate.measurement.ElapsedTime - firstUpdate.measurement.ElapsedTime

                    ThroughputMetrics(bytes, usecs, calcBytesPerSec(bytes, elapsedUsecs))
                }
            }

            val totalBytes = streamMetrics.sumOf { it.bytes }
            val totalBytesPerSec = streamMetrics.sumOf { it.bytesPerSec }
            return ThroughputMetrics(totalBytes, usecs, totalBytesPerSec)
        }

        private fun calcBytesPerSec(bytes: Long, usecs: Long): Double {
            return bytes.toDouble() / (usecs.toDouble() / 1000000.0)
        }

        private fun calcBytesPerSec(update: ThroughputUpdate): Double {
            return calcBytesPerSec(getBytesReceived(update), update.measurement.ElapsedTime)
        }

        private fun getBytesReceived(update: ThroughputUpdate): Long {
            return (update.measurement.Network ?: update.measurement.Application).BytesReceived
        }
    }
}
