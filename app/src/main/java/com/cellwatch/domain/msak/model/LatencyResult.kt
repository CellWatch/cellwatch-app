package com.cellwatch.domain.msak.model

import kotlinx.datetime.Instant

data class LatencyResult(
    val start: Instant,
    val usecs: Long,
    val success: Boolean,
    val meanRtt: Int,
    val jitter: Int,
    val packetsSent: Int,
    val packetsReceived: Int,
)
