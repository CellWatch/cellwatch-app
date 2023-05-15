package com.example.ndt8

import android.util.Log
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.WebSocket
import okio.ByteString.Companion.toByteString
import kotlin.concurrent.thread
import kotlin.random.Random.Default.nextBytes

class Ndt8Sender(
    streamNum: Int,
    measurementChan: Channel<Pair<Boolean, Ndt8Measurement>>,
): Ndt8Listener(streamNum, measurementChan) {
    override fun onOpen(webSocket: WebSocket) {
        super.onOpen(webSocket)

        thread {
            try {
                runBlocking { sendData(webSocket) }
            } catch (e: Exception) {
                Log.e(TAG, "unexpected error sending data", e)
                webSocket.close(WS_CODE_INTERNAL_ERROR, null)
            }
        }
    }

    override fun onMeasurement(webSocket: WebSocket, measurement: Ndt8Measurement) {
        super.onMeasurement(webSocket, measurement)
        latestMeasurement = measurement
    }

    private suspend fun sendData(webSocket: WebSocket) {
        var size = NDT8_MIN_MESSAGE_SIZE
        var message = nextBytes(size).toByteString()
        while (send(webSocket, message)) {
            Log.v(TAG, "sent $size byte message")

            while (webSocket.queueSize() > 8 * size) {
                delay(1)
            }

            if (size < NDT8_MAX_SCALED_MESSAGE_SIZE && size < bytesSent.get() / NDT8_MESSAGE_SCALING_FRACTION) {
                size = size shl 1
                message = nextBytes(size).toByteString()
                Log.d(TAG, "scaled message size to $size bytes")
            }
        }
    }
}