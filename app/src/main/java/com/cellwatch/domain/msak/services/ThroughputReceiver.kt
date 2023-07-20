package com.cellwatch.domain.msak.services

import android.os.SystemClock
import com.cellwatch.domain.msak.model.MsakMeasurement
import kotlinx.coroutines.channels.Channel

class ThroughputReceiver(
    streamNum: Int,
    measurementChan: Channel<Pair<Boolean, MsakMeasurement>>,
): ThroughputListener(streamNum, measurementChan) {

    override var latestMeasurement: MsakMeasurement? = null
        get() {
            val usec = endUsec ?: (SystemClock.elapsedRealtimeNanos() / 1000)
            return MsakMeasurement(bytesSent.get(), bytesReceived.get(), usec - startUsec)
        }
}
