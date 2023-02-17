package com.example.ndtm

data class NdtMMeasurement(
    val Origin: String?,
    val AppInfo: AppInfo?,
    val ConnectionInfo: ConnectionInfo? = null,
    // TODO: add BBRInfo and TCPInfo
)

data class AppInfo(
    val NumBytes: Long,
    val ElapsedTime : Long,
)

data class ConnectionInfo(
    val Client: String,
    val Server: String,
    val UUID: String,
    val CC: String,
)

data class NdtMTestMetrics(
    val bytesPerSec: Double,
    val bytes: Long,
    val usecs: Long,
)

fun measurementToMetrics(measurement: NdtMMeasurement?): NdtMTestMetrics? {
    if (measurement == null) return null
    val bytes = measurement.AppInfo?.NumBytes ?: 0L
    val usecs = measurement.AppInfo?.ElapsedTime ?: 0L
    val bytesPerSec = calcBytesPerSec(bytes, usecs)
    return NdtMTestMetrics(bytesPerSec, bytes, usecs)
}

fun calcBytesPerSec(bytes: Long, usecs: Long): Double {
    return if (usecs == 0L) 0.0 else bytes / usecs * 1e6
}