package edu.gatech.cc.cellwatch.domain.msak.latency

import kotlinx.datetime.Instant

data class LatencyUpdate(
    val time: Instant,
    val message: LatencyMessage,
)
