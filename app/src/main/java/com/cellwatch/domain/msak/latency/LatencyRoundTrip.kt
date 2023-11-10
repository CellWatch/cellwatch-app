package com.cellwatch.domain.msak.latency

data class LatencyRoundTrip(
    val RTT: Int,
    val Lost: Boolean,
)
