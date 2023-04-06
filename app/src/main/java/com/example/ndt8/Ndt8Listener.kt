package com.example.ndt8

import android.os.SystemClock
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.atomic.AtomicLong

open class Ndt8Listener(
    streamNum: Int,
    private val measurementChan: Channel<Pair<Boolean, Ndt8Measurement>>,
): WebSocketListener() {
    protected val TAG = "${this::class.simpleName} $streamNum"
    protected var startUsec: Long = 0
    protected var endUsec: Long? = null
    protected var bytesSent = AtomicLong(0)
    protected var bytesReceived = AtomicLong(0)
    private var lastMeasurementUsec: Long = 0
    open var latestMeasurement: Ndt8Measurement? = null
        protected set

    final override fun onOpen(webSocket: WebSocket, response: Response) {
        super.onOpen(webSocket, response)
        startUsec = SystemClock.elapsedRealtimeNanos() / 1000
        lastMeasurementUsec = startUsec
        onOpen(webSocket)
    }

    final override fun onMessage(webSocket: WebSocket, text: String) {
        super.onMessage(webSocket, text)
        Log.v(TAG, "got text message: $text")
        bytesReceived.addAndGet(text.toByteArray().size.toLong())

        val wireMeasurement = try {
            Gson().fromJson(text, Ndt8Measurement::class.java)
        } catch (e: JsonSyntaxException) {
            Log.w(TAG, "text message deserialization failed", e)
            return
        }

        onMeasurement(webSocket, wireMeasurement)
        sendMeasurement(webSocket)
    }

    final override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
        super.onMessage(webSocket, bytes)
        Log.v(TAG, "got binary message of size ${bytes.size}")
        bytesReceived.addAndGet(bytes.size.toLong())
        sendMeasurement(webSocket)
    }

    final override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        super.onClosing(webSocket, code, reason)
        Log.d(TAG, "websocket closing: $code $reason")
        webSocket.close(WS_CODE_NORMAL_CLOSURE, null)
    }

    final override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        super.onClosed(webSocket, code, reason)
        Log.d(TAG, "websocket closed: $code $reason")
        endUsec = SystemClock.elapsedRealtimeNanos() / 1000

        if (code == WS_CODE_NORMAL_CLOSURE || code == WS_CODE_GOING_AWAY) {
            measurementChan.close()
        } else {
            measurementChan.close(Ndt8UnexpectedCloseException(code, reason))
        }
    }

    final override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        super.onFailure(webSocket, t, response)
        Log.d(TAG, "websocket failure: $response", t)
        endUsec = SystemClock.elapsedRealtimeNanos() / 1000
        measurementChan.close(t)
    }

    open fun onOpen(webSocket: WebSocket) {}

    open fun onMeasurement(webSocket: WebSocket, measurement: Ndt8Measurement) {
        try {
            runBlocking { measurementChan.send(Pair(true, measurement)) }
        } catch (t: Throwable) {
            Log.w(TAG, "sending measurement on chan failed", t)
        }
    }

    fun send(webSocket: WebSocket, bytes: ByteString): Boolean {
        val sent = webSocket.send(bytes)
        if (sent) {
            bytesSent.addAndGet(bytes.size.toLong())
        }
        return sent
    }

    private fun send(webSocket: WebSocket, text: String): Boolean {
        val sent = webSocket.send(text)
        if (sent) {
            bytesSent.addAndGet(text.toByteArray().size.toLong())
        }
        return sent
    }

    private fun sendMeasurement(webSocket: WebSocket) {
        val usec = endUsec ?: (SystemClock.elapsedRealtimeNanos() / 1000)
        if (usec - lastMeasurementUsec < NDT8_MEASUREMENT_INTERVAL_MILLIS * 1000) {
            return
        }

        lastMeasurementUsec = usec
        val measurement = Ndt8Measurement(
            bytesSent.get(),
            bytesReceived.get(),
            usec - startUsec,
        )

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
