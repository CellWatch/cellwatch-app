package com.cellwatch.domain.msak.model

import com.cellwatch.data.model.Cell
import com.cellwatch.data.model.Location
import kotlinx.datetime.Instant

data class LatencyResult(
    val start: Instant? = null,
    val usecs: Long? = null,
    val success: Boolean,
    val remoteAddr: String? = null,
    val meanRtt: Int? = null,
    val jitter: Int? = null,
    val packetsSent: Int? = null,
    val packetsReceived: Int? = null,
)

data class FullLatencyResult(
    val latencyResult: LatencyResult,
    var locations: List<Location>,
    var cells: List<Cell>? = null
)