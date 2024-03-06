package edu.gatech.cc.cellwatch.data.network.model

import edu.gatech.cc.cellwatch.data.model.LatencyData
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class NetworkLatencyData(
    var id: String = UUID.randomUUID().toString(),

    @SerialName("measurement_id")
    var measurementId: String? = null, // UUID

    val rtt: Int? = null,

    val jitter: Int? = null,

    val sent: Int? = null,

    val received: Int? = null,

    val servers: List<String>? = null,

    @SerialName("created_on")
    val createdOn: Instant? = null,

    @SerialName("updated_on")
    val updatedOn: Instant? = null
)

fun NetworkLatencyData.asExternalModel() = LatencyData(
    id, measurementId, rtt, jitter, sent, received, servers, createdOn, updatedOn
)
