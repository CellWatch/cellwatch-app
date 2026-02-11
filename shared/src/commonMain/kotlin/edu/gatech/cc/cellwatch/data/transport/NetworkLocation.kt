package edu.gatech.cc.cellwatch.data.transport

import com.benasher44.uuid.uuid4
import edu.gatech.cc.cellwatch.domain.model.Location
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NetworkLocation(
    var id: String = uuid4().toString(),
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
    val updatedOn: Instant? = null,
)

fun Location.toNetwork(): NetworkLocation = NetworkLocation(
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
    updatedOn = updatedOn,
)

fun NetworkLocation.toDomainOrNull(): Location? {
    val latitude = lat ?: return null
    val longitude = lon ?: return null
    return Location(
        id = id,
        timestamp = timestamp,
        lat = latitude,
        lon = longitude,
        accuracy = accuracy,
        speed = speed,
        speedAccuracy = speedAccuracy,
        heading = heading,
        measurementId = measurementId,
        createdOn = createdOn,
        updatedOn = updatedOn,
    )
}
