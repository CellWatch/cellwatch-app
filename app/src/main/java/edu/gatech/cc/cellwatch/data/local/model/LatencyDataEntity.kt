package edu.gatech.cc.cellwatch.data.local.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import edu.gatech.cc.cellwatch.data.model.LatencyData
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
data class LatencyDataEntity(
    @PrimaryKey
    var id: String = UUID.randomUUID().toString(),

    @ColumnInfo(index = true)
    var measurementId: String? = null, // UUID

    val rtt: Int? = null,

    val jitter: Int? = null,

    val sent: Int? = null,

    val received: Int? = null,

    val servers: List<String>? = null,

//    @ColumnInfo(name = "created_on")
    val createdOn: Instant? = null,

//    @ColumnInfo(name = "updated_on")
    val updatedOn: Instant? = null
)

fun LatencyDataEntity.asExternalModel() = LatencyData(
    id, measurementId, rtt, jitter, sent, received, servers, createdOn, updatedOn
)
