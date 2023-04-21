package com.example.ndt8.data.entities

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Relation
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
@Entity
data class Measurement(
    @PrimaryKey
    var id: String = UUID.randomUUID().toString(),

//    @SerialName("measurement_id")
//    val measurementId: String = UUID.randomUUID().toString(),

    @SerialName("group_id")
    @ColumnInfo(name = "group_id")
    val groupId: String? = null,

    @SerialName("campaign_id")
    @ColumnInfo(name = "campaign_id")
    val campaignId: String? = null,

    @SerialName("session_id")
    @ColumnInfo(name = "session_id")
    val sessionId: String? = null,

    @SerialName("device_id")
    @ColumnInfo(name = "device_id")
    val deviceId: String? = null,

    @SerialName("device_manufacturer")
    @ColumnInfo(name = "device_manufacturer")
    val deviceManufacturer: String? = null,

    @SerialName("device_model")
    @ColumnInfo(name = "device_model")
    val deviceModel: String? = null,

    @SerialName("device_os_name")
    @ColumnInfo(name = "device_os_name")
    val deviceOsName: String? = null,

    @SerialName("device_os_version")
    @ColumnInfo(name = "device_os_version")
    val deviceOsVersion: String? = null,

    @SerialName("app_name")
    @ColumnInfo(name = "app_name")
    val appName: String? = null,

    val provider: String? = null,

    val type: String = "download",

    val timestamp: Instant? = null,

    val duration: Long? = null,

    val scheduled: Boolean? = null,

    val success: Boolean? = null,

    @SerialName("carrier_aggregation")
    @ColumnInfo(name = "carrier_aggregation")
    val carrierAggregation: Boolean? = null,

    @SerialName("network_connected")
    @ColumnInfo(name = "network_connected")
    val networkConnected: Boolean? = null,

    @SerialName("network_available")
    @ColumnInfo(name = "network_available")
    val networkAvailable: Boolean? = null,

    @SerialName("network_roaming")
    @ColumnInfo(name = "network_roaming")
    val networkRoaming: Boolean? = null,

//    @SerialName("data_id")
//    @ColumnInfo(name = "data_id")
//    var dataId: String? = null,

//    @SerialName("latency_data_id")
//    @ColumnInfo(name = "latency_data_id")
//    var latencyDataId: String? = null,

    @SerialName("extra_data")
    @ColumnInfo(name = "extra_data")
    val extraData: String? = null,

    @SerialName("created_on")
    @ColumnInfo(name = "created_on")
    val createdOn: Instant,

    @SerialName("updated_on")
    @ColumnInfo(name = "updated_on")
    val updatedOn: Instant
)

data class MeasurementWithUploadDownloadData(
    @Embedded val measurement: Measurement,
    @Relation(
        parentColumn = "id",
        entityColumn = "measurement_id"
    )
    val uploadDownloadData: List<UploadDownloadData>
)

data class MeasurementAndLatencyData(
    @Embedded val measurement: Measurement,
    @Relation(
        parentColumn = "id",
        entityColumn = "measurement_id"
    )
    val latencyData: LatencyData
)

data class MeasurementWithAllData(
    @Embedded val measurement: Measurement,

    @Relation(
        parentColumn = "id",
        entityColumn = "measurement_id"
    )
    val uploadDownloadData: List<UploadDownloadData>,

    @Relation(
        parentColumn = "id",
        entityColumn = "measurement_id"
    )
    val latencyData: LatencyData,

    @Relation(
        parentColumn = "id",
        entityColumn = "measurement_id"
    )
    val locations: List<Location>
)

//fun NdtMMeasurementToMeasurement(ndtMeasurement: NdtMMeasurement): Measurement? {
//    if (ndtMeasurement == null) return null
//
//    val measurement = Measurement(
//
//    )
//}