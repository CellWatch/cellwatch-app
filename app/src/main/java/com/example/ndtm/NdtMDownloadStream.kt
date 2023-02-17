package com.example.ndtm

import android.util.Log
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import kotlin.concurrent.thread

class NdtMDownloadStream(
    private val num: Int,
    private val client: OkHttpClient,
    private val url: String,
) {
    private val TAG = NdtMDownloadStream::class.simpleName
    private var webSocket: WebSocket? = null
    private var complete = false
    private val measurementChan = Channel<NdtMMeasurement>()
    private val listener = NdtMReceiver(measurementChan)
    private val _updateChan = Channel<NdtMMeasurement>()
    val updateChan: ReceiveChannel<NdtMMeasurement> = _updateChan
    var success = true
        private set
    var readyToEndWarmup = false
        private set
    val measurements = ArrayList<NdtMMeasurement>()
    private var endWarmupMeasurement: NdtMMeasurement? = null
    private val latestMeasurement
        get() = if (measurements.isEmpty()) null else measurements.last()
    val warmupMetrics: NdtMTestMetrics?
        get() = measurementToMetrics(endWarmupMeasurement)
    val activeMetrics: NdtMTestMetrics?
        get() {
            val warmup = warmupMetrics
            val latest = measurementToMetrics(latestMeasurement)
            if (warmup == null || latest == null) return null
            val bytes = latest.bytes - warmup.bytes
            val usecs = latest.usecs - warmup.usecs
            return NdtMTestMetrics(calcBytesPerSec(bytes, usecs), bytes, usecs)
        }
    val currentMetrics: NdtMTestMetrics?
        get() = activeMetrics ?: measurementToMetrics(latestMeasurement)

    fun start() {
        thread {
            try {
                runBlocking { run() }
                onComplete()
            } catch (e: Exception) {
                Log.e(TAG, "unexpected error running stream $num", e)
                webSocket?.close(WS_CODE_GOING_AWAY, null)
                onComplete(e)
            }
        }
    }

    fun cancel(error: Boolean) {
        if (complete) return
        onComplete()

        Log.d(TAG, "stream $num cancelled ${if (error) "with" else "without"} error")
        webSocket?.close(if (error) WS_CODE_GOING_AWAY else WS_CODE_NORMAL_CLOSURE, null)
    }

    fun endWarmup() {
        endWarmupMeasurement = listener.getMeasurement()
    }

    private suspend fun run() {
        val request = Request.Builder()
            .url(url)
            .header("Sec-WebSocket-Protocol", NDTM_WS_PROTO)
            .header("User-Agent", NDTM_USER_AGENT)
            .build()
        webSocket = client.newWebSocket(request, listener)

        try {
            measurementChan.consumeEach { onMeasurementReceived(it) }
            addMeasurement()
        } catch (t: Throwable) {
            Log.d(TAG, "download stream $num failed", t)
            success = false
            addMeasurement()

            if (t is NdtMUnexpectedCloseException) {
                onComplete(t)
            }
        }
    }

    private fun onMeasurementReceived(measurement: NdtMMeasurement) {
        Log.v(TAG, "got measurement from stream $num")
        if (measurement.Origin != "receiver") {
            return
        }

        if (!readyToEndWarmup && isReadyToEndWarmup(measurement)) {
            Log.d(TAG, "stream $num ready to end warmup")
            readyToEndWarmup = true
        }

        addMeasurement(measurement)
    }

    private fun onComplete(t: Throwable? = null) {
        complete = true
        _updateChan.close(t)
    }

    private fun addMeasurement(measurement: NdtMMeasurement? = null) {
        val m = measurement ?: listener.getMeasurement()
        measurements.add(m)
        _updateChan.trySend(m)
    }

    private fun isReadyToEndWarmup(curMeasurement: NdtMMeasurement): Boolean {
        val lastBPS = measurementToMetrics(latestMeasurement)?.bytesPerSec ?: return false
        val curBPS = measurementToMetrics(curMeasurement)?.bytesPerSec ?: return false
        if (lastBPS == 0.0 || curBPS == 0.0) return false

        return curBPS <= lastBPS
    }
}