package com.cellwatch.domain.fcc

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.cellwatch.domain.msak.Server
import com.cellwatch.domain.msak.throughput.ThroughputDirection
import com.cellwatch.domain.msak.throughput.ThroughputTest
import com.cellwatch.domain.msak.throughput.ThroughputUpdate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class ThroughputTest(
    server: Server,
    private val streams: Int,
    private val direction: ThroughputDirection,
    measurementId: String? = null,
) {
    private val TAG = this::class.simpleName
    private val maxWarmupTime = 5 * 1000L
    private val maxActiveTime = 10 * 1000L
    private val msakTest = ThroughputTest(
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

    suspend fun run(): ThroughputResult {
        try {
            msakTest.start()
            handler.postDelayed({ catchErrors { startActive() } }, maxWarmupTime)
            msakTest.updatesChan.consumeEach { handleUpdate(it) }

            // Assume start time was set since we started the test above.
            val startTime = msakTest.startTime!!

            val warmupMetrics = calcAggregateMetrics(
                ((activeStartTime ?: Clock.System.now()) - startTime).inWholeMicroseconds,
                lastWarmupUpdates ?: latestUpdates,
            )

            val activeMetrics = calcAggregateMetrics(
                ((msakTest.endTime ?: Clock.System.now()) - (activeStartTime ?: startTime)).inWholeMicroseconds,
                latestUpdates,
                lastWarmupUpdates,
            )

            return ThroughputResult(
                msakTest.serverHost,
                error == null && msakTest.streams.all { it.error == null },
                startTime,
                warmupMetrics,
                activeMetrics,
            )
        } catch (c: CancellationException) {
            Log.i(TAG, "throughput test cancelled")
            msakTest.stop()
            throw c
        } catch (e: Exception) {
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
            lastWarmupUpdates = msakTest.streams.map { s -> s.updates.last { isFromReceiver(it) } }
        } catch (e: NoSuchElementException) {
            error = e
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
        val updates = msakTest.streams[stream].updates.filter { isFromReceiver(it) }
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
        } catch (e: Exception) {
            Log.e(TAG, "unexpected error running throughput test", e)
            error = e
        }
    }
}