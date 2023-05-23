package com.cellwatch.data.local.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.cellwatch.data.model.Measurement
import kotlinx.datetime.Instant
import java.util.UUID

@Entity
data class MeasurementEntity(
    @PrimaryKey
    var id: String = UUID.randomUUID().toString(),

//    @ColumnInfo(name = "group_id")
    val groupId: String? = null,

//    @ColumnInfo(name = "campaign_id")
    val campaignId: String? = null,

//    @ColumnInfo(name = "session_id")
    val sessionId: String? = null,

//    @ColumnInfo(name = "device_id")
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
    extraData,
    createdOn,
    updatedOn
)