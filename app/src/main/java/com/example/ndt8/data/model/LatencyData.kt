package com.example.ndt8.data.model

import com.example.ndt8.data.local.model.LatencyDataEntity
import com.example.ndt8.data.network.model.NetworkLatencyData
import kotlinx.datetime.Instant
import java.util.UUID

/**
 * External data layer representation of a lotency data record
 */
data class LatencyData(
    var id: String = UUID.randomUUID().toString(),
    var measurementId: String? = null, // UUID
    val rtt: Long? = null,
    val jitter: Long? = null,
    val sent: Long? = null,
    val received: Long? = null,
    val servers: List<String>? = null,
    val createdOn: Instant? = null,
    val updatedOn: Instant? = null
)

fun LatencyData.asEntity() = LatencyDataEntity(
    id, measurementId, rtt, jitter, sent, received, servers, createdOn, updatedOn
)

fun LatencyData.asNetworkModel() = NetworkLatencyData(
    id, measurementId, rtt, jitter, sent, received, servers, createdOn, updatedOn
)