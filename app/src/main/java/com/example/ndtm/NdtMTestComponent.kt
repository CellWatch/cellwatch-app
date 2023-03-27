package com.example.ndtm

import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import java.util.*
import kotlin.concurrent.schedule

class NdtMTestComponent(
    private val client: OkHttpClient,
    server: NdtMLocateServer,
    measurementId: String,
    direction: NdtMTestDirection,
    numStreams: Int,
) {
    private val TAG = NdtMTestComponent::class.simpleName
    private val url = getUrl(server, direction, measurementId)
    private val streams = Array(numStreams) { NdtMStream(it, client, url, direction) }
    private var startUsec: Long = 0
    private var warmupUsec: Long? = null
    private var endUsec: Long? = null
    private val maxWarmupDurationTimer = Timer()
    private val progressChan = Channel<NdtMTestMetrics>()
    val progress: ReceiveChannel<NdtMTestMetrics> = progressChan

    suspend fun run(): NdtMTestResult {
        val maxDurationTimer = Timer()
        maxDurationTimer.schedule(NDTM_MAX_MILLIS) {
            Log.d(TAG, "max test duration reached")
            for (stream in streams) stream.cancel(false)
        }

        maxWarmupDurationTimer.schedule(NDTM_MAX_WARMUP_MILLIS) {
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

        return NdtMTestResult(
            streams.all { it.success },
            getAggregateMetrics(AggregateMetricType.WARMUP),
            if (warmupUsec != null) getAggregateMetrics(AggregateMetricType.ACTIVE) else null,
            streams.map { it.measurements },
        )
    }

    private fun onUpdateReceived() {
        val currentMetrics = getAggregateMetrics(AggregateMetricType.CURRENT)
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

    private fun getAggregateMetrics(type: AggregateMetricType): NdtMTestMetrics {
        val start = when (type) {
            AggregateMetricType.CURRENT, AggregateMetricType.WARMUP -> startUsec
            AggregateMetricType.ACTIVE -> warmupUsec
        } ?: throw Throwable("missing start usec value for aggregate metrics $type")

        val end = when (type) {
            AggregateMetricType.CURRENT -> SystemClock.elapsedRealtimeNanos() / 1000
            AggregateMetricType.WARMUP -> warmupUsec ?: startUsec
            AggregateMetricType.ACTIVE -> endUsec
        } ?: throw Throwable("missing end usec value for aggregate metrics $type")

        var bytes = 0L
        var bytesPerSec = 0.0
        for (stream in streams) {
            val metrics = when(type) {
                AggregateMetricType.CURRENT -> stream.currentMetrics
                AggregateMetricType.WARMUP -> stream.warmupMetrics
                AggregateMetricType.ACTIVE -> stream.activeMetrics
            } ?: continue

            bytes += metrics.bytes
            bytesPerSec += metrics.bytesPerSec
        }

        return NdtMTestMetrics(bytesPerSec, bytes, end - start)
    }

    private enum class AggregateMetricType { CURRENT, WARMUP, ACTIVE }
}

@Serializable
data class NdtMTestResult(
    val success: Boolean,
    val warmupMetrics: NdtMTestMetrics?,
    val activeMetrics: NdtMTestMetrics?,
    val measurements: Collection<Collection<NdtMMeasurement>>,
)

enum class NdtMTestDirection{ UPLOAD, DOWNLOAD }