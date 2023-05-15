package com.example.ndt8.data.local.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import com.example.ndt8.data.model.Location
import kotlinx.datetime.Instant
import java.util.UUID

@Entity(
    foreignKeys = [
        ForeignKey(entity = MeasurementEntity::class,
            parentColumns = ["id"],
            childColumns = ["measurementId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class LocationEntity(
    @PrimaryKey
    var id: String = UUID.randomUUID().toString(),

    val timestamp: Instant? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    val accuracy: Double? = null,
    val speed: Double? = null,

//    @ColumnInfo(name = "speed_accuracy")
    val speedAccuracy: Double? = null,

    val heading: Double? = null,

//    @ColumnInfo(name = "measurement_id", index = true)
    @ColumnInfo(index = true)
    var measurementId: String? = null,

//    @ColumnInfo(name = "created_on")
    val createdOn: Instant? = null,

//    @ColumnInfo(name = "updated_on")
    val updatedOn: Instant? = null
)

fun LocationEntity.asExternalModel() = Location(
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