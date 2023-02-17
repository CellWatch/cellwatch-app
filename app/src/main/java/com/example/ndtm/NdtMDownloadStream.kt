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
    private val updateChan = Channel<NdtMDownloadStreamUpdate>()
    private var webSocket: WebSocket? = null

    val updates: ReceiveChannel<NdtMDownloadStreamUpdate> = updateChan
    var latestUpdate: NdtMDownloadStreamUpdate? = null
        private set


    fun start() {
        thread {
            try {
                runBlocking { run() }
                updateChan.close()
            } catch (e: Exception) {
                Log.e(TAG, "unexpected error running stream $num", e)
                webSocket?.close(WS_CODE_GOING_AWAY, null)
                updateChan.close(e)
            }
        }
    }

    fun cancel(error: Boolean) {
        Log.d(TAG, "stream $num cancelled ${if (error) "with" else "without"} error")
        webSocket?.close(if (error) WS_CODE_GOING_AWAY else WS_CODE_NORMAL_CLOSURE, null)
    }

    private suspend fun run() {
        val request = Request.Builder()
            .url(url)
            .header("Sec-WebSocket-Protocol", NDTM_WS_PROTO)
            .header("User-Agent", NDTM_USER_AGENT)
            .build()
        val measurementChan = Channel<NdtMMeasurement>()
        val listener = NdtMReceiver(measurementChan)
        webSocket = client.newWebSocket(request, listener)

        try {
            measurementChan.consumeEach { measurement ->
                Log.v(TAG, "got measurement from stream $num")
                if (measurement.Origin == "receiver") {
                    val update = NdtMDownloadStreamUpdate(true, measurement)
                    latestUpdate = update
                    updateChan.trySend(update)
                }
            }
        } catch (t: Throwable) {
            Log.d(TAG, "download stream $num failed", t)

            if (t is NdtMUnexpectedCloseException) {
                updateChan.close(t)
            }
        }
    }
}

data class NdtMDownloadStreamUpdate(
    val success: Boolean,
    val measurement: NdtMMeasurement,
)