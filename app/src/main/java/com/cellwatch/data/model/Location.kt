package com.cellwatch.data.model

import com.cellwatch.data.local.model.LocationEntity
import com.cellwatch.data.network.model.NetworkLocation
import kotlinx.datetime.Instant
import java.util.UUID

/**
 * External data layer representation of a location
 */
data class Location (
    var id: String = UUID.randomUUID().toString(),
    val timestamp: Instant? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val accuracy: Double? = null,
    val speed: Double? = null,
    val speedAccuracy: Double? = null,
    val heading: Double? = null,
    var measurementId: String? = null,
    val createdOn: Instant? = null,
    val updatedOn: Instant? = null
) {
    companion object {
        fun fromAndroidLocation(androidLocation: android.location.Location?): Location? {
            return if (androidLocation != null) Location(
                timestamp = Instant.fromEpochMilliseconds(androidLocation.time),
                lat = androidLocation.latitude,
                lon = androidLocation.longitude,
                accuracy = if (androidLocation.hasAccuracy())
                    androidLocation.accuracy.toDouble() else null,
                speed = if (androidLocation.hasSpeed())
                    androidLocation.speed.toDouble() else null,
                speedAccuracy = if (androidLocation.hasSpeedAccuracy())
                    androidLocation.speedAccuracyMetersPerSecond.toDouble() else null,
                heading = if (androidLocation.hasBearing())
                    androidLocation.bearing.toDouble() else null
            ) else null
        }
    }
}

fun Location.asEntity() = LocationEntity(
    id, timestamp, lat, lon, accuracy, speed, speedAccuracy, heading, measurementId, createdOn, updatedOn
)

fun Location.asNetworkModel() = NetworkLocation(
    id, timestamp, lat, lon, accuracy, speed, speedAccuracy, heading, measurementId, createdOn, updatedOn
)

//fun Location.fromAndroidLocation(androidLocation: android.location.Location) =
//    Location(
//        timestamp = Instant.fromEpochMilliseconds(androidLocation.time),
//        lat = androidLocation.latitude,
//        lon = androidLocation.longitude,
//        accuracy = if (androidLocation.hasAccuracy())
//            androidLocation.accuracy.toDouble() else null,
//        speed = if (androidLocation.hasSpeed())
//            androidLocation.speed.toDouble() else null,
//        speedAccuracy = if (androidLocation.hasSpeedAccuracy())
//            androidLocation.speedAccuracyMetersPerSecond.toDouble() else null,
//        heading = if (androidLocation.hasBearing())
//            androidLocation.bearing.toDouble() else null
//    )