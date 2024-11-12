package edu.gatech.cc.cellwatch.data.local.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import edu.gatech.cc.cellwatch.data.model.UploadDownloadData
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

    @ColumnInfo(index = true)
    var measurementId: String? = null, // UUID

    val warmupDuration: Long? = null,

    val warmupBytes: Long? = null,

    val duration: Long? = null,

    val bytes: Long? = null,

    val bytesPerSec: Double? = null,

    val applicationBytes: Long? = null,

    val applicationBytesPerSec: Double? = null,

    val servers: List<String>? = null,

    val createdOn: Instant? = null,

    val updatedOn: Instant? = null
)

fun UploadDownloadDataEntity.asExternalModel() = UploadDownloadData(
    id,
    measurementId,
    warmupDuration,
    warmupBytes,
    duration,
    bytes,
    bytesPerSec,
    applicationBytes,
    applicationBytesPerSec,
    servers,
    createdOn,
    updatedOn
)