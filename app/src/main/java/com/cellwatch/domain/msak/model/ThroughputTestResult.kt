package com.cellwatch.domain.msak.model

import kotlinx.serialization.Serializable

@Serializable
data class ThroughputTestResult(
    val success: Boolean,
    val warmupMetrics: MsakTestMetrics?,
    val activeMetrics: MsakTestMetrics?,
    val streamResults: Collection<ThroughputStreamResult?>,
)