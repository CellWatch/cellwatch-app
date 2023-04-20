package com.example.ndt8.data.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@Entity
data class UploadDownloadData(
    @PrimaryKey val id: String, // UUID

    @SerialName("measurement_id")
    @ColumnInfo(name = "measurement_id")
    val measurementId: String, // UUID

    @SerialName("warmup_duration")
    @ColumnInfo(name = "warmup_duration")
    val warmupDuration: Long?,

    @SerialName("warmup_bytes")
    @ColumnInfo(name = "warmup_bytes")
    val warmupBytes: Long?,

    val duration: Long?,

    val bytes: Long?,

    val servers: List<String>,

    @SerialName("created_on")
    @ColumnInfo(name = "created_on")
    val createdOn: Instant,

    @SerialName("updated_on")
    @ColumnInfo(name = "updated_on")
    val updatedOn: Instant
)