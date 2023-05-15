package com.example.ndt8.domain.ndt8.services

import android.os.SystemClock
import com.example.ndt8.domain.ndt8.model.Ndt8Measurement
import kotlinx.coroutines.channels.Channel

class Ndt8Receiver(
    streamNum: Int,
    measurementChan: Channel<Pair<Boolean, Ndt8Measurement>>,
): Ndt8Listener(streamNum, measurementChan) {

    override var latestMeasurement: Ndt8Measurement? = null
        get() {
            val usec = endUsec ?: (SystemClock.elapsedRealtimeNanos() / 1000)
            return Ndt8Measurement(bytesSent.get(), bytesReceived.get(), usec - startUsec)
        }
}
