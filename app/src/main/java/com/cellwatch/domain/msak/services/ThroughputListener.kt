package com.cellwatch.domain.msak.services

import android.os.SystemClock
import android.util.Log
import com.cellwatch.domain.msak.model.ByteCounters
import com.cellwatch.domain.msak.util.UnexpectedCloseException
import com.cellwatch.domain.msak.util.WS_CODE_GOING_AWAY
import com.cellwatch.domain.msak.util.WS_CODE_NORMAL_CLOSURE
import com.cellwatch.domain.msak.model.ThroughputMeasurement
import com.cellwatch.domain.msak.util.MemorylessTicker
import com.cellwatch.domain.msak.util.THROUGHPUT_AVG_MEASUREMENT_INTERVAL_MILLIS
import com.cellwatch.domain.msak.util.THROUGHPUT_MAX_MEASUREMENT_INTERVAL_MILLIS
import com.cellwatch.domain.msak.util.THROUGHPUT_MIN_MEASUREMENT_INTERVAL_MILLIS
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.atomic.AtomicLong

open class ThroughputListener(
    streamNum: Int,
    private val measurementChan: Channel<Pair<Boolean, ThroughputMeasurement>>,
    private val sockFactory: ThroughputSocketFactory,
): WebSocketListener() {
    protected val TAG = "${this::class.simpleName} $streamNum"
    protected var startUsec: Long = 0
    protected var endUsec: Long? = null
    protected var appBytesSent = AtomicLong(0)
    protected var appBytesReceived = AtomicLong(0)
    protected val netBytesSent
        get() = sockFactory.throughputSock?.outBytes ?: 0
    protected val netBytesReceived
        get() = sockFactory.throughputSock?.inBytes ?: 0
    //private var lastMeasurementUsec: Long = 0
    open var latestMeasurement: ThroughputMeasurement? = null
        protected set

    private val measurementTicker = MemorylessTicker(
        THROUGHPUT_AVG_MEASUREMENT_INTERVAL_MILLIS,
        THROUGHPUT_MAX_MEASUREMENT_INTERVAL_MILLIS,
        THROUGHPUT_MIN_MEASUREMENT_INTERVAL_MILLIS,
    )

    final override fun onOpen(webSocket: WebSocket, response: Response) {
        super.onOpen(webSocket, response)
        startUsec = SystemClock.elapsedRealtimeNanos() / 1000
        measurementTicker.start { sendMeasurement(webSocket) }
        onOpen(webSocket)
    }

    final override fun onMessage(webSocket: WebSocket, text: String) {
        super.onMessage(webSocket, text)
        Log.v(TAG, "got text message: $text")
        appBytesReceived.addAndGet(text.toByteArray().size.toLong())

        val wireMeasurement = try {
            Gson().fromJson(text, ThroughputMeasurement::class.java)
        } catch (e: JsonSyntaxException) {
            Log.w(TAG, "text message deserialization failed", e)
            return
        }

        onMeasurement(webSocket, wireMeasurement)
        //sendMeasurement(webSocket)
    }

    final override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
        super.onMessage(webSocket, bytes)
        Log.v(TAG, "got binary message of size ${bytes.size}")
        appBytesReceived.addAndGet(bytes.size.toLong())
        //sendMeasurement(webSocket)
    }

    final override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        super.onClosing(webSocket, code, reason)
        Log.d(TAG, "websocket closing: $code $reason")
        webSocket.close(WS_CODE_NORMAL_CLOSURE, null)
        measurementTicker.stop()
    }

    final override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        super.onClosed(webSocket, code, reason)
        Log.d(TAG, "websocket closed: $code $reason")
        endUsec = SystemClock.elapsedRealtimeNanos() / 1000

        if (code == WS_CODE_NORMAL_CLOSURE || code == WS_CODE_GOING_AWAY) {
            measurementChan.close()
        } else {
            measurementChan.close(UnexpectedCloseException(code, reason))
        }

        measurementTicker.stop()
    }

    final override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        super.onFailure(webSocket, t, response)
        Log.d(TAG, "websocket failure: $response", t)
        endUsec = SystemClock.elapsedRealtimeNanos() / 1000
        measurementChan.close(t)
        measurementTicker.stop()
    }

    open fun onOpen(webSocket: WebSocket) {}

    @OptIn(ExperimentalCoroutinesApi::class)
    open fun onMeasurement(webSocket: WebSocket, measurement: ThroughputMeasurement) {
        if (!measurementChan.isClosedForSend) {
            try {
                runBlocking { measurementChan.send(Pair(true, measurement)) }
            } catch (t: Throwable) {
                Log.w(TAG, "sending measurement on chan failed", t)
            }
        }
    }

    fun send(webSocket: WebSocket, bytes: ByteString): Boolean {
        val sent = webSocket.send(bytes)
        if (sent) {
            appBytesSent.addAndGet(bytes.size.toLong())
        }
        return sent
    }

    private fun send(webSocket: WebSocket, text: String): Boolean {
        val sent = webSocket.send(text)
        if (sent) {
            appBytesSent.addAndGet(text.toByteArray().size.toLong())
        }
        return sent
    }

    protected fun makeMeasurement(): ThroughputMeasurement {
        val usec = endUsec ?: (SystemClock.elapsedRealtimeNanos() / 1000)
        return ThroughputMeasurement(
            netBytesSent,
            netBytesReceived,
            ByteCounters(netBytesSent, netBytesReceived),
            ByteCounters(appBytesSent.get(), appBytesReceived.get()),
            usec - startUsec
        )
    }

    private fun sendMeasurement(webSocket: WebSocket) {
        val measurement = makeMeasurement()

        Log.d(TAG, "sending measurement: $measurement")
        if (send(webSocket, Gson().toJson(measurement))) {
            try {
                runBlocking { measurementChan.send(Pair(false, measurement)) }
            } catch (t: Throwable) {
                Log.w(TAG, "sending measurement on chan failed", t)
            }
        } else {
            Log.d(TAG, "unable to send measurement")
        }
    }
}
