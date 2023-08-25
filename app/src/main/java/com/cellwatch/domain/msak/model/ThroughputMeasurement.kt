package com.cellwatch.domain.msak.model

import kotlinx.serialization.Serializable

@Serializable
data class ThroughputMeasurement(
    // TODO: remove top-level BytesSent, BytesReceived and make Application mandatory once schema change goes to prod
    val BytesSent: Long?,
    val BytesReceived: Long?,
    val Network: ByteCounters?,
    val Application: ByteCounters?,
    val ElapsedTime: Long,

    // TODO: add BBRInfo and TCPInfo

    // WireMeasurement fields, only sent once by server
    val CC: String? = null,
    val UUID: String? = null,
    val LocalAddr: String? = null,
    val RemoteAddr: String? = null,
)