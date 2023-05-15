package com.example.ndt8.domain.ndt8.services

import android.util.Log
import com.example.ndt8.domain.ndt8.util.NDT8_USER_AGENT
import com.example.ndt8.domain.ndt8.util.NDT8_WS_PROTO
import com.example.ndt8.domain.ndt8.util.Ndt8UnexpectedCloseException
import com.example.ndt8.domain.ndt8.util.WS_CODE_GOING_AWAY
import com.example.ndt8.domain.ndt8.util.WS_CODE_NORMAL_CLOSURE
import com.example.ndt8.domain.ndt8.model.Ndt8Measurement
import com.example.ndt8.domain.ndt8.model.Ndt8TestDirection
import com.example.ndt8.domain.ndt8.model.Ndt8TestMetrics
import com.example.ndt8.domain.ndt8.mappers.measurementToMetrics
import com.example.ndt8.domain.ndt8.usecases.calcBytesPerSec
import com.example.ndt8.domain.ndt8.util.NDT8_CONNECT_TIMEOUT_MILLIS
import com.example.ndt8.domain.ndt8.util.NDT8_READ_TIMEOUT_MILLIS
import com.example.ndt8.domain.ndt8.util.NDT8_WRITE_TIMEOUT_MILLIS
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.collections.ArrayList
import kotlin.concurrent.schedule
import kotlin.concurrent.thread

class Ndt8Stream(
    num: Int,
    private val client: OkHttpClient,
    private val url: String,
    private val direction: Ndt8TestDirection,
) {
    private val TAG = "${Ndt8Stream::class.simpleName} $num"
    private var webSocket: WebSocket? = null
    private var complete = false
    private val measurementChan = Channel<Pair<Boolean, Ndt8Measurement>>()
    private val listener = when (direction) {
        Ndt8TestDirection.UPLOAD -> Ndt8Sender(num, measurementChan)
        Ndt8TestDirection.DOWNLOAD -> Ndt8Receiver(num, measurementChan)
    }
    private val _updateChan = Channel<Ndt8Measurement>()
    val updateChan: ReceiveChannel<Ndt8Measurement> = _updateChan
    private var success = true
    var readyToEndWarmup = false
        private set
    private val measurements = ArrayList<Ndt8Measurement>()
    private var endWarmupMeasurement: Ndt8Measurement? = null
    private val latestMeasurement
        get() = if (measurements.isEmpty()) null else measurements.last()
    val activeMetrics: Ndt8TestMetrics?
        get() {
            val warmup = measurementToMetrics(endWarmupMeasurement)
            val latest = measurementToMetrics(latestMeasurement)
            if (warmup == null || latest == null) return null
            val bytes = latest.bytes - warmup.bytes
            val usecs = latest.usecs - warmup.usecs
            return Ndt8TestMetrics(calcBytesPerSec(bytes, usecs), bytes, usecs)
        }
    val currentMetrics: Ndt8TestMetrics?
        get() = activeMetrics ?: measurementToMetrics(latestMeasurement)
    var result: Ndt8StreamResult? = null
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
            .connectTimeout(NDT8_CONNECT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
            .readTimeout(NDT8_READ_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
            .writeTimeout(NDT8_WRITE_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
            .build()

        val request = Request.Builder()
            .url(url)
            .header("Sec-WebSocket-Protocol", NDT8_WS_PROTO)
            .header("User-Agent", NDT8_USER_AGENT)
            .build()

        webSocket = requestClient.newWebSocket(request, listener)

        try {
            measurementChan.consumeEach { onMeasurementReceived(it.first, it.second) }
            addMeasurement()
        } catch (t: Throwable) {
            Log.d(TAG, "stream failed", t)
            success = false
            addMeasurement()

            if (t is Ndt8UnexpectedCloseException) {
                onComplete(t)
            }
        }
    }

    private fun onMeasurementReceived(fromServer: Boolean, measurement: Ndt8Measurement) {
        Log.v(TAG, "got measurement from stream")
        if (fromServer) {
            cc = measurement.CC ?: cc
            uuid = measurement.UUID ?: uuid
            localAddr = measurement.LocalAddr ?: localAddr
            remoteAddr = measurement.RemoteAddr ?: remoteAddr
        }

        if ((direction == Ndt8TestDirection.DOWNLOAD) == fromServer) {
            return
        }

        checkReadyToEndWarmup(measurement)
        addMeasurement(measurement)
    }

    private fun onComplete(t: Throwable? = null) {
        complete = true
        result = Ndt8StreamResult(
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

    private fun addMeasurement(measurement: Ndt8Measurement? = null) {
        val m = measurement ?: listener.latestMeasurement ?: return
        measurements.add(m)
        _updateChan.trySend(m)
    }

    private fun checkReadyToEndWarmup(curMeasurement: Ndt8Measurement) {
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

@Serializable
data class Ndt8StreamResult (
    val success: Boolean,
    val cc: String?,
    val uuid: String?,
    val localAddr: String?,
    val remoteAddr: String?,
    val warmupMetrics: Ndt8TestMetrics?,
    val activeMetrics: Ndt8TestMetrics?,
    val measurements: Collection<Ndt8Measurement>
)