package edu.gatech.cc.cellwatch.data.transport

import com.benasher44.uuid.uuid4
import edu.gatech.cc.cellwatch.domain.model.LatencyData
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NetworkLatencyData(
    var id: String = uuid4().toString(),
    @SerialName("measurement_id")
    var measurementId: String? = null,
    val rtt: Int? = null,
    val jitter: Int? = null,
    val sent: Int? = null,
    val received: Int? = null,
    val servers: List<String>? = null,
    @SerialName("created_on")
    val createdOn: Instant? = null,
    @SerialName("updated_on")
    val updatedOn: Instant? = null,
)

fun LatencyData.toNetwork(): NetworkLatencyData = NetworkLatencyData(
    id = id,
    measurementId = measurementId,
    rtt = rtt,
    jitter = jitter,
    sent = sent,
    received = received,
    servers = servers,
    createdOn = createdOn,
    updatedOn = updatedOn,
)

fun NetworkLatencyData.toDomain(): LatencyData = LatencyData(
    id = id,
    measurementId = measurementId,
    rtt = rtt,
    jitter = jitter,
    sent = sent,
    received = received,
    servers = servers,
    createdOn = createdOn,
    updatedOn = updatedOn,
)
