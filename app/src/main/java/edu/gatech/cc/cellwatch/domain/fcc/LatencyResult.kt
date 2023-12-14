package edu.gatech.cc.cellwatch.domain.fcc

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
