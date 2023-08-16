package com.cellwatch.domain.msak.model

import kotlinx.datetime.Instant

data class LatencyResult(
    val targetHost: String,
    val success: Boolean,
    val start: Instant,
    val usecs: Long,
    val meanRtt: Int,
    val jitter: Int,
    val packetsSent: Int,
    val packetsReceived: Int,
)
