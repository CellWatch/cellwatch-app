package com.example.ndt8.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@Entity
data class LatencyData(
    @PrimaryKey val id: String, // UUID

    @SerialName("measurement_id")
    @ColumnInfo(name = "measurement_id")
    val measurementId: String, // UUID

    val rtt: Long?,

    val jitter: Long?,

    val sent: Long?,

    val received: Long?,

    val servers: List<String>,

    @SerialName("created_on")
    @ColumnInfo(name = "created_on")
    val createdOn: Instant,

    @SerialName("updated_on")
    @ColumnInfo(name = "updated_on")
    val updatedOn: Instant
)