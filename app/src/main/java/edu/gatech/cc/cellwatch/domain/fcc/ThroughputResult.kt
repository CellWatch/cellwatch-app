package edu.gatech.cc.cellwatch.domain.fcc

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
