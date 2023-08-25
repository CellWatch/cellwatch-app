package com.cellwatch.domain.msak.mappers

import com.cellwatch.domain.msak.model.ThroughputMeasurement
import com.cellwatch.domain.msak.model.ThroughputTestMetrics
import com.cellwatch.domain.msak.usecases.calcBytesPerSec

fun measurementToMetrics(measurement: ThroughputMeasurement?): ThroughputTestMetrics? {
    if (measurement == null) return null

    val bytesReceived = measurement.Network?.BytesReceived ?:
        measurement.Application?.BytesReceived ?:
        measurement.BytesReceived ?:
        0

    return ThroughputTestMetrics(
        calcBytesPerSec(bytesReceived, measurement.ElapsedTime),
        bytesReceived,
        measurement.ElapsedTime
    )
}