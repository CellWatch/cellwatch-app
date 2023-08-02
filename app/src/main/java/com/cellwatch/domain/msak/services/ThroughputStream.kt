package com.cellwatch.domain.msak.services

import android.util.Log
import com.cellwatch.domain.msak.util.THROUGHPUT_USER_AGENT
import com.cellwatch.domain.msak.util.THROUGHPUT_WS_PROTO
import com.cellwatch.domain.msak.util.UnexpectedCloseException
import com.cellwatch.domain.msak.util.WS_CODE_GOING_AWAY
import com.cellwatch.domain.msak.util.WS_CODE_NORMAL_CLOSURE
import com.cellwatch.domain.msak.model.MsakMeasurement
import com.cellwatch.domain.msak.model.ThroughputTestDirection
import com.cellwatch.domain.msak.model.ThroughputTestMetrics
import com.cellwatch.domain.msak.mappers.measurementToMetrics
import com.cellwatch.domain.msak.model.ThroughputStreamResult
import com.cellwatch.domain.msak.usecases.calcBytesPerSec
import com.cellwatch.domain.msak.util.MSAK_CONNECT_TIMEOUT_MILLIS
import com.cellwatch.domain.msak.util.MSAK_READ_TIMEOUT_MILLIS
import com.cellwatch.domain.msak.util.MSAK_WRITE_TIMEOUT_MILLIS
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList
import kotlin.concurrent.schedule
import kotlin.concurrent.thread

class ThroughputStream(
    num: Int,
    private val client: OkHttpClient,
    private val url: String,
    private val direction: ThroughputTestDirection,
) {
    private val TAG = "${ThroughputStream::class.simpleName} $num"
    private var webSocket: WebSocket? = null
    private var complete = false
    private val measurementChan = Channel<Pair<Boolean, MsakMeasurement>>()
    private val listener = when (direction) {
        ThroughputTestDirection.UPLOAD -> ThroughputSender(num, measurementChan)
        ThroughputTestDirection.DOWNLOAD -> ThroughputReceiver(num, measurementChan)
    }
    private val _updateChan = Channel<MsakMeasurement>()
    val updateChan: ReceiveChannel<MsakMeasurement> = _updateChan
    private var success = true
    var readyToEndWarmup = false
        private set
    private val measurements = ArrayList<MsakMeasurement>()
    private var endWarmupMeasurement: MsakMeasurement? = null
    private val latestMeasurement
        get() = if (measurements.isEmpty()) null else measurements.last()
    val activeMetrics: ThroughputTestMetrics?
        get() {
            val warmup = measurementToMetrics(endWarmupMeasurement)
            val latest = measurementToMetrics(latestMeasurement)
            if (warmup == null || latest == null) return null
            val bytes = latest.bytes - warmup.bytes
            val usecs = latest.usecs - warmup.usecs
            return ThroughputTestMetrics(calcBytesPerSec(bytes, usecs), bytes, usecs)
        }
    val currentMetrics: ThroughputTestMetrics?
        get() = activeMetrics ?: measurementToMetrics(latestMeasurement)
    var result: ThroughputStreamResult? = null
        private set

    // WireMeasurement connection-related fields sent by the server
    private var cc: String? = null
    private var uuid: String? = null
    private var localAddr: String? = null
    private var remoteAddr: String? = null

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
        measurementChan.close()
        Timer().schedule(5000L) { webSocket?.cancel() }
    }

    fun endWarmup() {
        endWarmupMeasurement = listener.latestMeasurement
    }

    private suspend fun run() {
        val requestClient = client.newBuilder()
            .connectTimeout(MSAK_CONNECT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
            .readTimeout(MSAK_READ_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
            .writeTimeout(MSAK_WRITE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
            .build()

        val request = Request.Builder()
            .url(url)
            .header("Sec-WebSocket-Protocol", THROUGHPUT_WS_PROTO)
            .header("User-Agent", THROUGHPUT_USER_AGENT)
            .build()

        webSocket = requestClient.newWebSocket(request, listener)

        try {
            measurementChan.consumeEach { onMeasurementReceived(it.first, it.second) }
            addMeasurement()
        } catch (t: Throwable) {
            Log.d(TAG, "stream failed", t)
            success = false
            addMeasurement()

            if (t is UnexpectedCloseException) {
                onComplete(t)
            }
        }
    }

    private fun onMeasurementReceived(fromServer: Boolean, measurement: MsakMeasurement) {
        Log.v(TAG, "got measurement from stream")
        if (fromServer) {
            cc = measurement.CC ?: cc
            uuid = measurement.UUID ?: uuid
            localAddr = measurement.LocalAddr ?: localAddr
            remoteAddr = measurement.RemoteAddr ?: remoteAddr
        }

        if ((direction == ThroughputTestDirection.DOWNLOAD) == fromServer) {
            return
        }

        checkReadyToEndWarmup(measurement)
        addMeasurement(measurement)
    }

    private fun onComplete(t: Throwable? = null) {
        complete = true
        result = ThroughputStreamResult(
            success,
            cc,
            uuid,
            localAddr,
            remoteAddr,
            measurementToMetrics(endWarmupMeasurement),
            activeMetrics,
            measurements,
        )
        _updateChan.close(t)
    }

    private fun addMeasurement(measurement: MsakMeasurement? = null) {
        val m = measurement ?: listener.latestMeasurement ?: return
        measurements.add(m)
        _updateChan.trySend(m)
    }

    private fun checkReadyToEndWarmup(curMeasurement: MsakMeasurement) {
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