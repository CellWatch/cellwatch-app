package com.example.ndtm

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

open class NdtMListener(
    streamNum: Int,
    protected val measurementChan: Channel<NdtMMeasurement>,
    protected val origin: String,
): WebSocketListener() {
    protected val TAG = "${this::class.simpleName} $streamNum"
    protected var startUsec: Long = 0
    protected var endUsec: Long? = null
    open var latestMeasurement: NdtMMeasurement? = null
        protected set

    override fun onOpen(webSocket: WebSocket, response: Response) {
        super.onOpen(webSocket, response)
        startUsec = SystemClock.elapsedRealtimeNanos() / 1000
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        super.onMessage(webSocket, text)
        Log.v(TAG, "got text message: $text")

        val measurement = try {
            Gson().fromJson(text, NdtMMeasurement::class.java)
        } catch (e: JsonSyntaxException) {
            Log.w(TAG, "text message deserialization failed", e)
            return
        }

        latestMeasurement = measurement

        try {
            runBlocking { measurementChan.send(measurement) }
        } catch (t: Throwable) {
            Log.w(TAG, "sending measurement on chan failed", t)
        }
    }

    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
        super.onMessage(webSocket, bytes)
        Log.v(TAG, "got binary message of size ${bytes.size}")
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        super.onClosing(webSocket, code, reason)
        Log.d(TAG, "websocket closing: $code $reason")
        webSocket.close(WS_CODE_NORMAL_CLOSURE, null)
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        super.onClosed(webSocket, code, reason)
        Log.d(TAG, "websocket closed: $code $reason")
        endUsec = SystemClock.elapsedRealtimeNanos() / 1000

        if (code == WS_CODE_NORMAL_CLOSURE || code == WS_CODE_GOING_AWAY) {
            measurementChan.close()
        } else {
            measurementChan.close(NdtMUnexpectedCloseException(code, reason))
        }
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        super.onFailure(webSocket, t, response)
        Log.d(TAG, "websocket failure: $response", t)
        endUsec = SystemClock.elapsedRealtimeNanos() / 1000
        measurementChan.close(t)
    }
}
