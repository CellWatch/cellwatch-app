package com.cellwatch.domain.msak.model

import com.cellwatch.data.model.Cell
import com.cellwatch.data.model.Location
import kotlinx.datetime.Instant
//import kotlinx.serialization.Serializable

//import kotlinx.serialization.Serializable

//@Serializable
data class ThroughputTestResult(
    val targetHost: String,
    val success: Boolean,
    val start: Instant,
    val warmupMetrics: ThroughputTestMetrics? = null,
    val activeMetrics: ThroughputTestMetrics? = null,
    val streamResults: List<ThroughputStreamResult?> = mutableListOf()
)

data class FullThroughputTestResult(
    val throughputTestResult: ThroughputTestResult,
    var locations: List<Location>,
    var cells: List<Cell>? = null
)