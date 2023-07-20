package com.cellwatch.domain.msak.model

import kotlinx.serialization.Serializable

@Serializable
data class MsakMeasurement(
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
