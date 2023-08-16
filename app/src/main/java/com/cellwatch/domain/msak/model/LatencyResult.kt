package com.cellwatch.domain.msak.model

import com.cellwatch.data.model.Cell
import com.cellwatch.data.model.Location
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

data class FullLatencyResult(
    val latencyResult: LatencyResult,
    var locations: List<Location>,
    var cells: List<Cell>? = null
)