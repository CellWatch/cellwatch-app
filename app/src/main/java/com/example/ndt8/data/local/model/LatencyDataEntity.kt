package com.example.ndt8.data.local.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import com.example.ndt8.data.model.LatencyData
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
data class LatencyDataEntity(
    @PrimaryKey
    var id: String = UUID.randomUUID().toString(),

//    @ColumnInfo(name = "measurement_id", index = true)
    @ColumnInfo(index = true)
    var measurementId: String? = null, // UUID

    val rtt: Long? = null,

    val jitter: Long? = null,

    val sent: Long? = null,

    val received: Long? = null,

    val servers: List<String>? = null,

//    @ColumnInfo(name = "created_on")
    val createdOn: Instant? = null,

//    @ColumnInfo(name = "updated_on")
    val updatedOn: Instant? = null
)

fun LatencyDataEntity.asExternalModel() = LatencyData(
    id, measurementId, rtt, jitter, sent, received, servers, createdOn, updatedOn
)