package edu.gatech.cc.cellwatch.domain.fcc

import android.os.Handler
import android.os.Looper
import edu.gatech.cc.cellwatch.core.util.Log
import edu.gatech.cc.cellwatch.domain.msak.Server
import edu.gatech.cc.cellwatch.domain.msak.throughput.ThroughputDirection
import edu.gatech.cc.cellwatch.domain.msak.throughput.ThroughputTest
import edu.gatech.cc.cellwatch.domain.msak.throughput.ThroughputUpdate
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
    private val maxWarmupTime: Long = 10000L,
    private val maxActiveTime: Long = 10000L,
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
        measurementId
    )
    private var activeStartTime: Instant? = null
    private val readyForActive = MutableList(streams) { false }
    private var lastWarmupUpdates: List<ThroughputUpdate>? = null
    private var error: Throwable? = null
    private val _metricsChan = Channel<ThroughputMetrics>(32)
    private val latestUpdates; get() = msakTest.streams.map {s ->s.updates.lastOrNull { isFromReceiver(it) }}
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
                return ThroughputResult(server.machine, false, Clock.System.now(), ThroughputMetrics(0, 0), ThroughputMetrics(0, 0))
            }

            msakTest.start()
            handler.postDelayed({ catchErrors { startActive() } }, maxWarmupTime)
            msakTest.updatesChan.consumeEach { handleUpdate(it) }

            val start = msakTest.startTime!! // assume set since we started the test above
            val activeStart = activeStartTime
            val latest = latestUpdates

            val warmupMetrics = calcAggregateMetrics(
                ((activeStart ?: Clock.System.now()) - start).inWholeMicroseconds,
                lastWarmupUpdates ?: latest,
            )

            val haveActiveUpdates = latest.all { it != null && it != lastWarmupUpdates?.get(it.stream) }
            val activeMetrics = if (activeStart != null && haveActiveUpdates) {
                calcAggregateMetrics(
                    ((msakTest.endTime ?: Clock.System.now()) - activeStart).inWholeMicroseconds,
                    latest,
                    lastWarmupUpdates,
                )
            } else ThroughputMetrics(0, 0)

            return ThroughputResult(
                msakTest.serverHost,
                error == null && msakTest.streams.all { it.error == null } && activeMetrics != null,
                start,
                warmupMetrics,
                activeMetrics,
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
            lastWarmupUpdates = msakTest.streams.mapIndexed() { i, stream ->
                // avoid using an iterator on the list of updates to prevent a ConcurrentModificationException
                // see https://stackoverflow.com/questions/27818867/java-concurrentmodificationexception-when-iterating-arraylist
                var update: ThroughputUpdate? = null
                for (i in (stream.updates.size - 1) downTo 0) {
                    val u = stream.updates[i]
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

        val curBPS = ThroughputMetrics.fromMeasurement(updates[updates.size - 1].measurement).bytesPerSec
        val lastBPS = ThroughputMetrics.fromMeasurement(updates[updates.size - 2].measurement).bytesPerSec

        return curBPS > 0 && lastBPS > 0 && curBPS <= lastBPS
    }

    private fun isFromReceiver(update: ThroughputUpdate): Boolean {
        return update.fromServer == (direction == ThroughputDirection.UPLOAD)
    }

    private fun calcAggregateMetrics(
        usecs: Long,
        lastUpdates: List<ThroughputUpdate?>,
        firstUpdates: List<ThroughputUpdate>? = null,
    ): ThroughputMetrics {
        val curMetrics = lastUpdates.map {
            if (it == null) {
                ThroughputMetrics(0, 0)
            } else {
                ThroughputMetrics.fromMeasurement(it.measurement)
            }
        }
        val effectiveMetrics = if (firstUpdates != null) {
            curMetrics.mapIndexed { i, m ->
                m - ThroughputMetrics.fromMeasurement(firstUpdates[i].measurement)
            }
        } else {
            curMetrics
        }

        val totalBytes = effectiveMetrics.sumOf { it.bytes }
        return ThroughputMetrics(totalBytes, usecs)
    }

    private fun catchErrors(fn: () -> Unit) {
        try {
            fn()
        } catch (e: Throwable) {
            Log.e(TAG, "unexpected error running throughput test", e)
            error = e
        }
    }

    private class MissingWarmupUpdateException: Exception("one or more streams had no updates in warmup period")
}
