package com.cellwatch.domain.msak.model

import kotlinx.serialization.Serializable

@Serializable
data class ThroughputStreamResult (
    val success: Boolean,
    val cc: String?,
    val uuid: String?,
    val localAddr: String?,
    val remoteAddr: String?,
    val warmupMetrics: MsakTestMetrics?,
    val activeMetrics: MsakTestMetrics?,
    val measurements: Collection<MsakMeasurement>
)