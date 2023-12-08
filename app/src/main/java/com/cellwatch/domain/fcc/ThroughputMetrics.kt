package com.cellwatch.domain.fcc

import com.cellwatch.domain.msak.throughput.ThroughputMeasurement
import kotlinx.serialization.Serializable

@Serializable
data class ThroughputMetrics(
    val bytes: Long,
    val usecs: Long,
) {
    companion object {
        fun fromMeasurement(measurement: ThroughputMeasurement): ThroughputMetrics {
            val bytesReceived = measurement.Network?.BytesReceived
                ?: measurement.Application?.BytesReceived
                ?: 0

            return ThroughputMetrics(bytesReceived, measurement.ElapsedTime)
        }
    }

    val bytesPerSec; get() = if (usecs == 0L) 0.0 else bytes.toDouble() / usecs.toDouble() * 1e6

    override fun toString(): String {
        return "${"%.2f".format(8 * bytesPerSec / 1e6)} Mbps, ${bytes} bytes, ${usecs / 1e6} secs"
    }

    operator fun minus(other: ThroughputMetrics): ThroughputMetrics {
        val resultBytes = bytes - other.bytes
        val resultUsecs = usecs - other.usecs

        return ThroughputMetrics(resultBytes, resultUsecs)
    }
}