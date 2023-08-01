package com.cellwatch.domain.msak.services

import android.os.SystemClock
import android.util.Log
import com.cellwatch.domain.msak.model.LocateServer
import com.cellwatch.domain.msak.model.MsakTestDirection
import com.cellwatch.domain.msak.model.ThroughputTestMetrics
import com.cellwatch.domain.msak.model.ThroughputTestResult
import com.cellwatch.domain.msak.managers.LocateManager
import com.cellwatch.domain.msak.util.THROUGHPUT_MAX_MILLIS
import com.cellwatch.domain.msak.util.THROUGHPUT_MAX_WARMUP_MILLIS
import com.cellwatch.domain.msak.util.THROUGHPUT_STREAMS
import com.cellwatch.domain.msak.util.THROUGHPUT_STREAM_DELAY
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.consumeEach
import okhttp3.OkHttpClient
import java.util.*
import kotlin.concurrent.schedule

class ThroughputTestComponent(
    private val client: OkHttpClient,
    server: LocateServer,
    measurementId: String?,
    direction: MsakTestDirection,
) {
    private val TAG = ThroughputTestComponent::class.simpleName
    private val url = LocateManager.getThroughputUrl(server, direction, measurementId)
    private val streams = Array(THROUGHPUT_STREAMS) { ThroughputStream(it, client, url, direction) }
    private var startUsec: Long = 0
    private var warmupUsec: Long? = null
    private var endUsec: Long? = null
    private val maxWarmupDurationTimer = Timer()
    private val progressChan = Channel<ThroughputTestMetrics>()
    val progress: ReceiveChannel<ThroughputTestMetrics> = progressChan

    suspend fun run(): ThroughputTestResult {
        val maxDurationTimer = Timer()
        maxDurationTimer.schedule(THROUGHPUT_MAX_MILLIS) {
            Log.d(TAG, "max test duration reached")
            for (stream in streams) stream.cancel(false)
        }

        maxWarmupDurationTimer.schedule(THROUGHPUT_MAX_WARMUP_MILLIS) {
            Log.d(TAG, "max warmup duration reached")
            endWarmup()
        }

        try {
            startUsec = SystemClock.elapsedRealtimeNanos() / 1000

            coroutineScope {
                for (stream in streams) {
                    stream.start()

                    async(Dispatchers.IO) {
                        stream.updateChan.consumeEach { onUpdateReceived() }
                        for (s in streams) s.cancel(false)
                    }

                    delay(THROUGHPUT_STREAM_DELAY)
                }
            }

            endUsec = SystemClock.elapsedRealtimeNanos() / 1000
        } catch (c: CancellationException) {
            Log.i(TAG, "download test cancelled")
            for (stream in streams) stream.cancel(true)
            throw c
        } catch (t: Throwable) {
            Log.e(TAG, "unexpected error running download test", t)
            for (stream in streams) stream.cancel(true)
            throw t
        } finally {
            maxDurationTimer.cancel()
            maxWarmupDurationTimer.cancel()
            progressChan.close()
        }

        val warmupMetrics = aggregateMetrics((warmupUsec ?: startUsec) - startUsec) { stream ->
            stream.result?.warmupMetrics
        }

        val activeMetrics = if (warmupUsec != null) {
            val end = endUsec ?: throw Throwable("missing end time")
            aggregateMetrics(end - warmupUsec!!) { stream ->
                stream.result?.activeMetrics
            }
        } else null

        return ThroughputTestResult(
            streams.all { it.result?.success ?: false },
            warmupMetrics,
            activeMetrics,
            streams.map { it.result },
        )
    }

    private fun onUpdateReceived() {
        val currentUsec = SystemClock.elapsedRealtimeNanos() / 1000
        val currentMetrics = aggregateMetrics(currentUsec - startUsec) { stream ->
            stream.currentMetrics
        }
        progressChan.trySend(currentMetrics)

        if (warmupUsec == null && streams.all { it.readyToEndWarmup }) {
            endWarmup()
        }

        if (warmupUsec != null) {
            val activeBytes = streams.sumOf { it.activeMetrics?.bytes ?: 0 }
            if (activeBytes > 1e9) {
                Log.i(TAG, "$activeBytes > 1000 megabytes transferred; ending early")
                for (stream in streams) stream.cancel(false)
            }
        }
    }

    private fun endWarmup() {
        val usec = SystemClock.elapsedRealtimeNanos() / 1000
        maxWarmupDurationTimer.cancel()
        warmupUsec = usec
        Log.d(TAG, "test warmup complete after ${usec - startUsec} usecs")
        for (s in streams) s.endWarmup()
    }

    private fun aggregateMetrics(
        duration: Long,
        getMetrics: (stream: ThroughputStream) -> ThroughputTestMetrics?,
    ): ThroughputTestMetrics {
        var bytes = 0L
        var bytesPerSec = 0.0
        for (stream in streams) {
            val metrics = getMetrics(stream) ?: continue
            bytes += metrics.bytes
            bytesPerSec += metrics.bytesPerSec
        }

        return ThroughputTestMetrics(bytesPerSec, bytes, duration)
    }
}