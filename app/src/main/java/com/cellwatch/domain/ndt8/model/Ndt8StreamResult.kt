package com.cellwatch.domain.ndt8.model

import kotlinx.serialization.Serializable

@Serializable
data class Ndt8StreamResult (
    val success: Boolean,
    val cc: String?,
    val uuid: String?,
    val localAddr: String?,
    val remoteAddr: String?,
    val warmupMetrics: Ndt8TestMetrics?,
    val activeMetrics: Ndt8TestMetrics?,
    val measurements: Collection<Ndt8Measurement>
)