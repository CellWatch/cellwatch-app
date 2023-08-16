package com.cellwatch.domain.msak.model

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class ThroughputTestResult(
    val targetHost: String,
    val success: Boolean,
    val start: Instant,
    val warmupMetrics: ThroughputTestMetrics?,
    val activeMetrics: ThroughputTestMetrics?,
    val streamResults: Collection<ThroughputStreamResult?>,
)