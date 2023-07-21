package com.cellwatch.domain.msak.model

import kotlinx.serialization.Serializable

@Serializable
data class ThroughputTestResult(
    val success: Boolean,
    val warmupMetrics: ThroughputTestMetrics?,
    val activeMetrics: ThroughputTestMetrics?,
    val streamResults: Collection<ThroughputStreamResult?>,
)