package com.example.ndtm

import android.util.Log
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.Response
import okhttp3.WebSocket
import okio.ByteString.Companion.toByteString
import kotlin.concurrent.thread
import kotlin.random.Random.Default.nextBytes

class NdtMSender(
    streamNum: Int,
    measurementChan: Channel<NdtMMeasurement>,
): NdtMListener(streamNum, measurementChan, "sender") {
    override fun onOpen(webSocket: WebSocket, response: Response) {
        super.onOpen(webSocket, response)

        thread {
            try {
                runBlocking { sendData(webSocket) }
            } catch (e: Exception) {
                Log.e(TAG, "unexpected error sending data", e)
                webSocket.close(WS_CODE_INTERNAL_ERROR, null)
            }
        }
    }

    private suspend fun sendData(webSocket: WebSocket) {
        var size = NDTM_MIN_MESSAGE_SIZE
        var message = nextBytes(size).toByteString()
        var bytesSent = 0L
        while (webSocket.send(message)) {
            bytesSent += size
            Log.v(TAG, "sent $size byte message")

            while (webSocket.queueSize() > 8 * size) {
                delay(1)
            }

            if (size < NDTM_MAX_SCALED_MESSAGE_SIZE && size < bytesSent / NDTM_MESSAGE_SCALING_FRACTION) {
                size = size shl 1
                message = nextBytes(size).toByteString()
                Log.d(TAG, "scaled message size to $size bytes")
            }
        }
    }
}