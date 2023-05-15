package com.example.ndt8.data.network.model

import com.example.ndt8.data.model.Location
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class NetworkLocation(
    var id: String = UUID.randomUUID().toString(),

    val timestamp: Instant? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val accuracy: Double? = null,
    val speed: Double? = null,

    @SerialName("speed_accuracy")
    val speedAccuracy: Double? = null,

    val heading: Double? = null,

    @SerialName("measurement_id")
    var measurementId: String? = null,

    @SerialName("created_on")
    val createdOn: Instant? = null,

    @SerialName("updated_on")
    val updatedOn: Instant? = null
)

fun NetworkLocation.asExternalModel() = Location(
    id = id,
    timestamp = timestamp,
    lat = lat,
    lon = lon,
    accuracy = accuracy,
    speed = speed,
    speedAccuracy = speedAccuracy,
    heading = heading,
    measurementId = measurementId,
    createdOn = createdOn,
    updatedOn = updatedOn
)