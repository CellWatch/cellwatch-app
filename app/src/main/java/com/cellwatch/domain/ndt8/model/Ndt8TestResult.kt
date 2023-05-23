package com.cellwatch.domain.ndt8.model

import kotlinx.serialization.Serializable

@Serializable
data class Ndt8TestResult(
    val success: Boolean,
    val warmupMetrics: Ndt8TestMetrics?,
    val activeMetrics: Ndt8TestMetrics?,
    val streamResults: Collection<Ndt8StreamResult?>,
)