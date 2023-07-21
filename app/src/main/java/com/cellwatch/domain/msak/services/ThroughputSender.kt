package com.cellwatch.domain.msak.services

import android.util.Log
import com.cellwatch.domain.msak.util.THROUGHPUT_MAX_SCALED_MESSAGE_SIZE
import com.cellwatch.domain.msak.util.THROUGHPUT_MESSAGE_SCALING_FRACTION
import com.cellwatch.domain.msak.util.THROUGHPUT_MIN_MESSAGE_SIZE
import com.cellwatch.domain.msak.util.WS_CODE_INTERNAL_ERROR
import com.cellwatch.domain.msak.model.MsakMeasurement
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.WebSocket
import okio.ByteString.Companion.toByteString
import kotlin.concurrent.thread
import kotlin.random.Random.Default.nextBytes

class ThroughputSender(
    streamNum: Int,
    measurementChan: Channel<Pair<Boolean, MsakMeasurement>>,
): ThroughputListener(streamNum, measurementChan) {
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

    override fun onMeasurement(webSocket: WebSocket, measurement: MsakMeasurement) {
        super.onMeasurement(webSocket, measurement)
        latestMeasurement = measurement
    }

    private suspend fun sendData(webSocket: WebSocket) {
        var size = THROUGHPUT_MIN_MESSAGE_SIZE
        var message = nextBytes(size).toByteString()
        while (send(webSocket, message)) {
            Log.v(TAG, "sent $size byte message")

            while (webSocket.queueSize() > 8 * size) {
                delay(1)
            }

            if (size < THROUGHPUT_MAX_SCALED_MESSAGE_SIZE && size < bytesSent.get() / THROUGHPUT_MESSAGE_SCALING_FRACTION) {
                size = size shl 1
                message = nextBytes(size).toByteString()
                Log.d(TAG, "scaled message size to $size bytes")
            }
        }
    }
}