package edu.gatech.cc.cellwatch.data.local.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import edu.gatech.cc.cellwatch.data.model.Location
import kotlinx.datetime.Instant
import java.util.UUID

@Entity(
    foreignKeys = [
        ForeignKey(entity = MeasurementEntity::class,
            parentColumns = ["id"],
            childColumns = ["measurementId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
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

    val speedAccuracy: Double? = null,

    val heading: Double? = null,

    @ColumnInfo(index = true)
    var measurementId: String? = null,

    val createdOn: Instant? = null,

    val updatedOn: Instant? = null
)

fun LocationEntity.asExternalModel() = if (lat == null || lon == null) {
    null
} else {
    Location(
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
}
