package com.example.ndtm

import android.os.SystemClock
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.concurrent.atomic.AtomicLong

class NdtMReceiver(
    private val measurementChan: Channel<NdtMMeasurement>,
): WebSocketListener() {
    private val TAG = NdtMReceiver::class.simpleName
    private var startUsec: Long = 0
    private var lastMeasurementUsec: Long = 0
    private val measurementMutex = Mutex(false)
    private var numBytes = AtomicLong(0)

    override fun onOpen(webSocket: WebSocket, response: Response) {
        super.onOpen(webSocket, response)
        startUsec = SystemClock.elapsedRealtimeNanos() / 1000
        lastMeasurementUsec = startUsec
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        super.onMessage(webSocket, text)
        Log.d(TAG, "got text message: $text")

        // include this text message in bytes received
        numBytes.addAndGet(text.toByteArray().size.toLong())

        runBlocking {
            async {
                // parse the message and pass it on
                try {
                    val measurement = Gson().fromJson(text, NdtMMeasurement::class.java)
                    measurementChan.send(measurement)
                } catch (e: JsonSyntaxException) {
                    Log.w(TAG, "text message deserialization failed", e)
                }
            }

            async { sendMeasurement(webSocket) }
        }
    }

    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
        super.onMessage(webSocket, bytes)
        Log.v(TAG, "got binary message of size ${bytes.size}")

        // count the bytes
        numBytes.addAndGet(bytes.size.toLong())

        runBlocking { sendMeasurement(webSocket) }
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        super.onClosing(webSocket, code, reason)
        Log.d(TAG, "websocket closing: $code $reason")
        webSocket.close(WS_CODE_NORMAL_CLOSURE, null)
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        super.onClosed(webSocket, code, reason)
        Log.d(TAG, "websocket closed: $code $reason")
        if (code == WS_CODE_NORMAL_CLOSURE || code == WS_CODE_GOING_AWAY) {
            measurementChan.close()
        } else {
            measurementChan.close(NdtMUnexpectedCloseException(code, reason))
        }
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        super.onFailure(webSocket, t, response)
        Log.d(TAG, "websocket failure: $response", t)
        measurementChan.close(t)
    }

    suspend fun sendMeasurement(webSocket: WebSocket) {
        measurementMutex.withLock {
            val bytes = numBytes.get()
            val usec = SystemClock.elapsedRealtimeNanos() / 1000
            if (usec - lastMeasurementUsec < NDTM_MEASUREMENT_INTERVAL_MILLIS * 1000) {
                return
            }

            lastMeasurementUsec = usec
            val measurement = NdtMMeasurement("receiver", AppInfo(bytes, usec - startUsec))
            Log.d(TAG, "sending measurement: $measurement")
            if (webSocket.send(Gson().toJson(measurement))) {
                measurementChan.send(measurement)
            } else {
                Log.d(TAG, "unable to send measurement")
            }
        }
    }
}