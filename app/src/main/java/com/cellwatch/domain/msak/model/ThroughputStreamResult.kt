package com.cellwatch.domain.msak.model

import kotlinx.serialization.Serializable

@Serializable
data class ThroughputStreamResult (
    val success: Boolean,
    val cc: String? = null,
    val uuid: String? = null,
    val localAddr: String? = null,
    val remoteAddr: String? = null,
    val warmupMetrics: ThroughputTestMetrics? = null,
    val activeMetrics: ThroughputTestMetrics? = null,
    val measurements: Collection<MsakMeasurement> = mutableListOf()
)