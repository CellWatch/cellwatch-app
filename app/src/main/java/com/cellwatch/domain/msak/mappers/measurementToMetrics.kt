package com.cellwatch.domain.msak.mappers

import com.cellwatch.domain.msak.model.MsakMeasurement
import com.cellwatch.domain.msak.model.ThroughputTestMetrics
import com.cellwatch.domain.msak.usecases.calcBytesPerSec

fun measurementToMetrics(measurement: MsakMeasurement?): ThroughputTestMetrics? {
    if (measurement == null) return null

    return ThroughputTestMetrics(
        calcBytesPerSec(measurement.BytesReceived, measurement.ElapsedTime),
        measurement.BytesReceived,
        measurement.ElapsedTime
    )
}