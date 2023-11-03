package com.cellwatch.data.local.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import com.cellwatch.data.model.UploadDownloadData
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
data class UploadDownloadDataEntity(
    @PrimaryKey
    var id: String = UUID.randomUUID().toString(),

//    @PrimaryKey val id: String, // UUID

//    @ColumnInfo(name = "measurement_id")
    @ColumnInfo(index = true)
    var measurementId: String? = null, // UUID

//    @ColumnInfo(name = "warmup_duration")
    val warmupDuration: Long? = null,

//    @ColumnInfo(name = "warmup_bytes")
    val warmupBytes: Long? = null,

    val duration: Long? = null,

    val bytes: Long? = null,

    val applicationBytes: Long? = null,

    val servers: List<String>? = null,

//    @ColumnInfo(name = "created_on")
    val createdOn: Instant? = null,

//    @ColumnInfo(name = "updated_on")
    val updatedOn: Instant? = null
)

fun UploadDownloadDataEntity.asExternalModel() = UploadDownloadData(
    id, measurementId, warmupDuration, warmupBytes, duration, bytes, applicationBytes, servers, createdOn, updatedOn
)