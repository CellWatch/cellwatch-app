package com.example.ndt8.domain.ndt8.services

import android.os.SystemClock
import android.util.Log
import com.example.ndt8.domain.ndt8.model.Ndt8LocateServer
import com.example.ndt8.domain.ndt8.model.Ndt8TestDirection
import com.example.ndt8.domain.ndt8.model.Ndt8TestMetrics
import com.example.ndt8.domain.ndt8.model.Ndt8TestResult
import com.example.ndt8.domain.ndt8.managers.Ndt8LocateManager
import com.example.ndt8.domain.ndt8.util.NDT8_MAX_MILLIS
import com.example.ndt8.domain.ndt8.util.NDT8_MAX_WARMUP_MILLIS
import com.example.ndt8.domain.ndt8.util.NDT8_STREAMS
import com.example.ndt8.domain.ndt8.util.NDT8_STREAM_DELAY
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.consumeEach
import okhttp3.OkHttpClient
import java.util.*
import kotlin.concurrent.schedule

class Ndt8TestComponent(
    private val client: OkHttpClient,
    server: Ndt8LocateServer,
    measurementId: String?,
    direction: Ndt8TestDirection,
) {
    private val TAG = Ndt8TestComponent::class.simpleName
    private val url = Ndt8LocateManager.getUrl(server, direction, measurementId)
    private val streams = Array(NDT8_STREAMS) { Ndt8Stream(it, client, url, direction) }
    private var startUsec: Long = 0
    private var warmupUsec: Long? = null
    private var endUsec: Long? = null
    private val maxWarmupDurationTimer = Timer()
    private val progressChan = Channel<Ndt8TestMetrics>()
    val progress: ReceiveChannel<Ndt8TestMetrics> = progressChan

    suspend fun run(): Ndt8TestResult {
        val maxDurationTimer = Timer()
        maxDurationTimer.schedule(NDT8_MAX_MILLIS) {
            Log.d(TAG, "max test duration reached")
            for (stream in streams) stream.cancel(false)
        }

        maxWarmupDurationTimer.schedule(NDT8_MAX_WARMUP_MILLIS) {
            Log.d(TAG, "max warmup duration reached")
            endWarmup()
        }

        try {
            startUsec = SystemClock.elapsedRealtimeNanos() / 1000

            coroutineScope {
                for (stream in streams) {
                    stream.start()

                    async {
                        stream.updateChan.consumeEach { onUpdateReceived() }
                        for (s in streams) s.cancel(false)
                    }

                    delay(NDT8_STREAM_DELAY)
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

        return Ndt8TestResult(
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
        getMetrics: (stream: Ndt8Stream) -> Ndt8TestMetrics?,
    ): Ndt8TestMetrics {
        var bytes = 0L
        var bytesPerSec = 0.0
        for (stream in streams) {
            val metrics = getMetrics(stream) ?: continue
            bytes += metrics.bytes
            bytesPerSec += metrics.bytesPerSec
        }

        return Ndt8TestMetrics(bytesPerSec, bytes, duration)
    }
}