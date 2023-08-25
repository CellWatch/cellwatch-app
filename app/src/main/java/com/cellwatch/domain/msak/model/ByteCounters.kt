package com.cellwatch.domain.msak.model

import kotlinx.serialization.Serializable

@Serializable
data class ByteCounters(
    val BytesSent: Long,
    val BytesReceived: Long,
)
