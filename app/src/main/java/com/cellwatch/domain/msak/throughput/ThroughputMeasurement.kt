package com.cellwatch.domain.msak.throughput

import kotlinx.serialization.Serializable

@Serializable
data class ThroughputMeasurement(
    val Network: ByteCounters?,
    val Application: ByteCounters,
    val ElapsedTime: Long,

    // TODO: add BBRInfo and TCPInfo

    // WireMeasurement fields, only sent once by server
    val CC: String? = null,
    val UUID: String? = null,
    val LocalAddr: String? = null,
    val RemoteAddr: String? = null,
)