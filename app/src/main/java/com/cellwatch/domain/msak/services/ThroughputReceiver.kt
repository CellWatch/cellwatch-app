package com.cellwatch.domain.msak.services

import android.os.SystemClock
import com.cellwatch.domain.msak.model.ByteCounters
import com.cellwatch.domain.msak.model.ThroughputMeasurement
import kotlinx.coroutines.channels.Channel

class ThroughputReceiver(
    streamNum: Int,
    measurementChan: Channel<Pair<Boolean, ThroughputMeasurement>>,
    sockFactory: ThroughputSocketFactory,
): ThroughputListener(streamNum, measurementChan, sockFactory) {

    override var latestMeasurement: ThroughputMeasurement? = null
        get() = makeMeasurement()
}
