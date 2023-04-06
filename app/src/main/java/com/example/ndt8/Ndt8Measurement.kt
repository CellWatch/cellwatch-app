package com.example.ndt8

data class Ndt8Measurement(
    val BytesSent: Long,
    val BytesReceived: Long,
    val ElapsedTime: Long,
    // TODO: add BBRInfo and TCPInfo

    // WireMeasurement fields, only sent once by server
    val CC: String? = null,
    val UUID: String? = null,
    val LocalAddr: String? = null,
    val RemoteAddr: String? = null,
)

data class Ndt8TestMetrics(
    val bytesPerSec: Double,
    val bytes: Long,
    val usecs: Long,
) {
    override fun toString(): String {
        return "${"%.2f".format(8 * bytesPerSec / 1e6)} Mbps, ${bytes} bytes, ${usecs / 1e6} secs"
    }
}

fun measurementToMetrics(measurement: Ndt8Measurement?): Ndt8TestMetrics? {
    if (measurement == null) return null

    return Ndt8TestMetrics(
        calcBytesPerSec(measurement.BytesReceived, measurement.ElapsedTime),
        measurement.BytesReceived,
        measurement.ElapsedTime
    )
}

fun calcBytesPerSec(bytes: Long, usecs: Long): Double {
    return if (usecs == 0L) 0.0 else bytes.toDouble() / usecs.toDouble() * 1e6
}