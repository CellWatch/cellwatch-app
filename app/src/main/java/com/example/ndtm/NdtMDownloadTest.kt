package com.example.ndtm

import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.consumeEach
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import java.util.*
import kotlin.concurrent.schedule
import kotlin.concurrent.thread


class NdtMDownloadTest(
    private val client: OkHttpClient,
    private val url: String,
    private val streams: Int,
) {
    private val TAG = NdtMDownloadTest::class.simpleName
    private val streamLatestMeasurements = Array<NdtMMeasurement?>(streams) { null }
    private val streamCancellationChans = Array(streams) { Channel<Unit>() }
    private var startTime: Long = 0
    private var success = true
    private var discardResults = false
    private val progressChan = Channel<NdtMTestMetrics>()
    val progress: ReceiveChannel<NdtMTestMetrics> = progressChan

    suspend fun run(): NdtMTestResult {
        val maxDurationTimer = Timer()
        maxDurationTimer.schedule(NDTM_MAX_DURATION) {
            Log.d(TAG, "max test duration reached")
            repeat(streams) { streamCancellationChans[it].close() }
        }

        try {
            startTime = SystemClock.elapsedRealtimeNanos() / 1000
            val streamThreads = Array(streams) { thread { runStream(it) } }

            // wait for all streams to complete in a cancellation-friendly way
            while (streamThreads.filter { !it.isAlive }.size < streams) {
                yield()
            }
        } catch (c: CancellationException) {
            Log.i(TAG, "download test cancelled; cleaning up")
            repeat(streams) { streamCancellationChans[it].close(c) }
            throw c
        } catch (t: Throwable) {
            Log.e(TAG, "unexpected error running download test", t)
            repeat(streams) { streamCancellationChans[it].close(t) }
            throw t
        } finally {
            maxDurationTimer.cancel()
            progressChan.close()
        }

        if (discardResults) {
            throw Throwable("download test failed")
        }

        return NdtMTestResult(success, getLatestTestMetrics())
    }

    private fun runStream(num: Int) {
        var webSocket: WebSocket? = null

        try {
            val request = Request.Builder()
                .url(url)
                .header("Sec-WebSocket-Protocol", NDTM_WS_PROTO)
                .header("User-Agent", NDTM_USER_AGENT)
                .build()
            val measurementChan = Channel<NdtMMeasurement>()
            val listener = NdtMReceiver(measurementChan)
            webSocket = client.newWebSocket(request, listener)

            runBlocking {
                launch { listenStreamMeasurements(num, measurementChan) }
                launch { listenStreamCancellation(num, webSocket) }
            }
        } catch (e: Exception) { // unexpected error
            Log.e(TAG, "unexpected error running stream $num", e)
            webSocket?.close(WS_CODE_GOING_AWAY, null)
            repeat(streams) { streamCancellationChans[it].close(e) }
            discardResults = true
        }
    }

    private suspend fun listenStreamMeasurements(num: Int, chan: ReceiveChannel<NdtMMeasurement>) {
        try {
            chan.consumeEach { measurement ->
                Log.v(TAG, "got measurement from stream $num")
                if (measurement.Origin == "receiver") {
                    streamLatestMeasurements[num] = measurement
                    progressChan.trySend(getLatestTestMetrics())
                }
            }
        } catch (t: Throwable) {
            Log.d(TAG, "download stream $num failed", t)
            repeat(streams) { streamCancellationChans[it].close(t) }

            if (t is NdtMUnexpectedCloseException) { // unexpected server behavior
                discardResults = true
            } else { // websocket failed, likely network error
                success = false
            }
        }
    }

    private suspend fun listenStreamCancellation(num: Int, webSocket: WebSocket) {
        val result = streamCancellationChans[num].receiveCatching()
        val code = if (result.exceptionOrNull() == null) WS_CODE_NORMAL_CLOSURE else WS_CODE_GOING_AWAY
        webSocket.close(code, null)
    }

    private fun getLatestTestMetrics(): NdtMTestMetrics {
        val duration = SystemClock.elapsedRealtimeNanos() / 1000 - startTime
        var bytesTransferred: Long = 0
        var bytesPerSec = 0.0
        for (measurement in streamLatestMeasurements) {
            bytesTransferred += measurement?.AppInfo?.NumBytes ?: 0
            bytesPerSec += (measurement?.AppInfo?.NumBytes ?: 0) / (measurement?.AppInfo?.ElapsedTime ?: 1) * 1e6
        }

        return NdtMTestMetrics(bytesPerSec, bytesTransferred, duration)
    }
}

data class NdtMTestResult(
    val success: Boolean,
    val metrics: NdtMTestMetrics,
)

data class NdtMTestMetrics(
    val bytesPerSec: Double,
    val bytesTransferred: Long,
    val duration: Long,
)
