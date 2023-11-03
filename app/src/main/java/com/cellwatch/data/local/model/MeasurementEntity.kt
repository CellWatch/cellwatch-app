package com.cellwatch.data.local.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import com.cellwatch.data.model.Measurement
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import java.util.UUID

@Entity(
    foreignKeys = [
        ForeignKey(entity = FccSubmissionEntity::class,
            parentColumns = ["id"],
            childColumns = ["groupId"],
            onDelete = ForeignKey.SET_NULL,
            onUpdate = ForeignKey.CASCADE
        )
    ]
)
data class MeasurementEntity(
    @PrimaryKey
    var id: String = UUID.randomUUID().toString(),

    @ColumnInfo(index = true)
    var groupId: String? = null,

    @ColumnInfo(index = true)
    val campaignId: String? = null,

    @ColumnInfo(index = true)
    val sessionId: String? = null,

    @ColumnInfo(index = true)
    val deviceId: String? = null,

//    @ColumnInfo(name = "device_manufacturer")
    val deviceManufacturer: String? = null,

//    @ColumnInfo(name = "device_model")
    val deviceModel: String? = null,

//    @ColumnInfo(name = "device_os_name")
    val deviceOsName: String? = null,

//    @ColumnInfo(name = "device_os_version")
    val deviceOsVersion: String? = null,

//    @ColumnInfo(name = "app_name")
    val appName: String? = null,

    val provider: String? = null,

//    @SerialName("type")
    val type: String, // = "foo",

    val timestamp: Instant? = null,

    val duration: Long? = null,

    val scheduled: Boolean? = null,

    val success: Boolean? = null,

//    @ColumnInfo(name = "carrier_aggregation")
    val carrierAggregation: Boolean? = null,

//    @ColumnInfo(name = "network_connected")
    val networkConnected: Boolean? = null,

//    @ColumnInfo(name = "network_available")
    val networkAvailable: Boolean? = null,

//    @ColumnInfo(name = "network_roaming")
    val networkRoaming: Boolean? = null,

    val simMcc: String? = null,

    val simMnc: String? = null,

    val netMcc: String? = null,

    val netMnc: String? = null,

//    @SerialName("data_id")
//    @ColumnInfo(name = "data_id")
//    var dataId: String? = null,

//    @SerialName("latency_data_id")
//    @ColumnInfo(name = "latency_data_id")
//    var latencyDataId: String? = null,

//    @ColumnInfo(name = "extra_data")
    val extraData: String? = null,

//    @ColumnInfo(name = "created_on")
    val createdOn: Instant? = null,

//    @ColumnInfo(name = "updated_on")
    val updatedOn: Instant? = null,

    // Has this record been pushed to cloud storage?
    var isSynchronized: Boolean = false
)

fun MeasurementEntity.asExternalModel() = Measurement(
    id,
    groupId,
    campaignId,
    sessionId,
    deviceId,
    deviceManufacturer,
    deviceModel,
    deviceOsName,
    deviceOsVersion,
    appName,
    provider,
    type,
    timestamp,
    duration,
    scheduled,
    success,
    carrierAggregation,
    networkConnected,
    networkAvailable,
    networkRoaming,
    simMcc,
    simMnc,
    netMcc,
    netMnc,
    extraData,
    createdOn,
    updatedOn
)