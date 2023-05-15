package com.example.ndt8.domain.ndt8.model

import kotlinx.serialization.Serializable

@Serializable
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
