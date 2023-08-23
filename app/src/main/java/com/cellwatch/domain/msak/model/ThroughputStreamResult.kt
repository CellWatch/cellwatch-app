package com.cellwatch.domain.msak.model

import kotlinx.serialization.Serializable

@Serializable
data class ThroughputStreamResult (
    val success: Boolean,
    val cc: String?,
    val uuid: String?,
    val localAddr: String?,
    val remoteAddr: String?,
    val warmupMetrics: ThroughputTestMetrics?,
    val activeMetrics: ThroughputTestMetrics?,
    val measurements: Collection<ThroughputMeasurement>,
)