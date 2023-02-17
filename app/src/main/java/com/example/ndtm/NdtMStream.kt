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

class NdtMStream(
    num: Int,
    private val client: OkHttpClient,
    private val url: String,
    direction: NdtMTestDirection,
) {
    private val TAG = "${NdtMStream::class.simpleName} $num"
    private var webSocket: WebSocket? = null
    private var complete = false
    private val measurementChan = Channel<NdtMMeasurement>()
    private val listener = when (direction) {
        NdtMTestDirection.UPLOAD -> NdtMSender(num, measurementChan)
        NdtMTestDirection.DOWNLOAD -> NdtMReceiver(num, measurementChan)
    }
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
                Log.e(TAG, "unexpected error running stream", e)
                webSocket?.close(WS_CODE_GOING_AWAY, null)
                onComplete(e)
            }
        }
    }

    fun cancel(error: Boolean) {
        if (complete) return
        onComplete()

        Log.d(TAG, "stream cancelled ${if (error) "with" else "without"} error")
        webSocket?.close(if (error) WS_CODE_GOING_AWAY else WS_CODE_NORMAL_CLOSURE, null)
    }

    fun endWarmup() {
        endWarmupMeasurement = listener.latestMeasurement
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
            Log.d(TAG, "stream failed", t)
            success = false
            addMeasurement()

            if (t is NdtMUnexpectedCloseException) {
                onComplete(t)
            }
        }
    }

    private fun onMeasurementReceived(measurement: NdtMMeasurement) {
        Log.v(TAG, "got measurement from stream")
        if (measurement.Origin != "receiver") {
            return
        }

        checkReadyToEndWarmup(measurement)
        addMeasurement(measurement)
    }

    private fun onComplete(t: Throwable? = null) {
        complete = true
        _updateChan.close(t)
    }

    private fun addMeasurement(measurement: NdtMMeasurement? = null) {
        val m = measurement ?: listener.latestMeasurement ?: return
        measurements.add(m)
        _updateChan.trySend(m)
    }

    private fun checkReadyToEndWarmup(curMeasurement: NdtMMeasurement) {
        if (readyToEndWarmup) return

        val lastBPS = measurementToMetrics(latestMeasurement)?.bytesPerSec ?: return
        val curBPS = measurementToMetrics(curMeasurement)?.bytesPerSec ?: return
        if (lastBPS == 0.0 || curBPS == 0.0) return

        readyToEndWarmup = curBPS <= lastBPS
        if (readyToEndWarmup) {
            Log.d(TAG, "stream ready to end warmup: $lastBPS, $curBPS")
        }
    }
}