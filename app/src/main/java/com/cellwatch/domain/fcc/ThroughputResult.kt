package com.cellwatch.domain.fcc

import com.cellwatch.data.model.Cell
import com.cellwatch.data.model.Location
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class ThroughputResult(
    val targetHost: String,
    val success: Boolean,
    val start: Instant,
    val warmupMetrics: ThroughputMetrics?,
    val activeMetrics: ThroughputMetrics?,
)

data class FullThroughputResult(
    val throughputTestResult: ThroughputResult,
    var locations: List<Location>,
    var cells: List<Cell>? = null
)
