package com.cellwatch.data.network.model

import com.cellwatch.data.model.LatencyData
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class NetworkLatencyData(
    var id: String = UUID.randomUUID().toString(),

    @SerialName("measurement_id")
    var measurementId: String? = null, // UUID

    val rtt: Long? = null,

    val jitter: Long? = null,

    val sent: Long? = null,

    val received: Long? = null,

    val servers: List<String>? = null,

    @SerialName("created_on")
    val createdOn: Instant? = null,

    @SerialName("updated_on")
    val updatedOn: Instant? = null
)

fun NetworkLatencyData.asExternalModel() = LatencyData(
    id, measurementId, rtt, jitter, sent, received, servers, createdOn, updatedOn
)