package com.cellwatch.domain.msak.mappers

import com.cellwatch.domain.msak.model.MsakMeasurement
import com.cellwatch.domain.msak.model.MsakTestMetrics
import com.cellwatch.domain.msak.usecases.calcBytesPerSec

fun measurementToMetrics(measurement: MsakMeasurement?): MsakTestMetrics? {
    if (measurement == null) return null

    return MsakTestMetrics(
        calcBytesPerSec(measurement.BytesReceived, measurement.ElapsedTime),
        measurement.BytesReceived,
        measurement.ElapsedTime
    )
}