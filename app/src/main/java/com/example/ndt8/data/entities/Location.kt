package com.example.ndt8.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
@Entity(
    foreignKeys = [
        ForeignKey(entity = Measurement::class,
            parentColumns = ["id"],
            childColumns = ["measurement_id"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class Location(
    @PrimaryKey
    var id: String = UUID.randomUUID().toString(),

    val timestamp: Instant,
    val lat: Double?,
    val lon: Double?,
    val accuracy: Double?,
    val speed: Double?,

    @SerialName("speed_accuracy")
    @ColumnInfo(name = "speed_accuracy")
    val speedAccuracy: Double?,

    val heading: Double?,

    @SerialName("measurement_id")
    @ColumnInfo(name = "measurement_id", index = true)
    val measurementId: String? = null,

    @SerialName("created_on")
    @ColumnInfo(name = "created_on")
    val createdOn: Instant,

    @SerialName("updated_on")
    @ColumnInfo(name = "updated_on")
    val updatedOn: Instant
)