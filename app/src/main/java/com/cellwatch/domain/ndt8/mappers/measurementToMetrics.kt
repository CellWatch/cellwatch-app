package com.cellwatch.domain.ndt8.mappers

import com.cellwatch.domain.ndt8.model.Ndt8Measurement
import com.cellwatch.domain.ndt8.model.Ndt8TestMetrics
import com.cellwatch.domain.ndt8.usecases.calcBytesPerSec

fun measurementToMetrics(measurement: Ndt8Measurement?): Ndt8TestMetrics? {
    if (measurement == null) return null

    return Ndt8TestMetrics(
        calcBytesPerSec(measurement.BytesReceived, measurement.ElapsedTime),
        measurement.BytesReceived,
        measurement.ElapsedTime
    )
}