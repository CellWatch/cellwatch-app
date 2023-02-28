package com.example.ndtm

import android.os.SystemClock
import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import okhttp3.Response
import okhttp3.WebSocket
import okio.ByteString
import java.util.concurrent.atomic.AtomicLong

class NdtMReceiver(
    streamNum: Int,
    measurementChan: Channel<NdtMMeasurement>,
): NdtMListener(streamNum, measurementChan, "receiver") {
    private var lastMeasurementUsec: Long = 0
    private var bytesRecvd = AtomicLong(0)

    override var latestMeasurement: NdtMMeasurement? = null
        get() {
            val bytes = bytesRecvd.get()
            val usec = endUsec ?: (SystemClock.elapsedRealtimeNanos() / 1000)
            return NdtMMeasurement(origin, AppInfo(bytes, usec - startUsec))
        }

    override fun onOpen(webSocket: WebSocket, response: Response) {
        super.onOpen(webSocket, response)
        lastMeasurementUsec = startUsec
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        super.onMessage(webSocket, text)
        bytesRecvd.addAndGet(text.toByteArray().size.toLong())
        runBlocking { sendMeasurement(webSocket) }
    }

    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
        super.onMessage(webSocket, bytes)
        bytesRecvd.addAndGet(bytes.size.toLong())
        runBlocking { sendMeasurement(webSocket) }
    }

    private suspend fun sendMeasurement(webSocket: WebSocket) {
        val bytes = bytesRecvd.get()
        val usec = endUsec ?: (SystemClock.elapsedRealtimeNanos() / 1000)
        if (usec - lastMeasurementUsec < NDTM_MEASUREMENT_INTERVAL_MILLIS * 1000) {
            return
        }

        lastMeasurementUsec = usec
        val measurement = NdtMMeasurement(origin, AppInfo(bytes, usec - startUsec))
        Log.d(TAG, "sending measurement: $measurement")
        if (webSocket.send(Gson().toJson(measurement))) {
            try {
                measurementChan.send(measurement)
            } catch (t: Throwable) {
                Log.w(TAG, "unable to send measurement on chan", t)
            }
        } else {
            Log.d(TAG, "unable to send measurement")
        }
    }
}
