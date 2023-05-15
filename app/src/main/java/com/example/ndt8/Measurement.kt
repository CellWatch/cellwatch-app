package com.example.ndtm

import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MeasurementTest(
    val id: String,
    val type: String,
    val duration: Long,
    @SerialName("group_id")
    val groupId: String? = null,
    @SerialName("device_model")
    val deviceModel: String? = null,
    @SerialName("created_on")
    val createdOn: Instant,
)

@Serializable
data class Measurement(
    var id: String? = null,

    @SerialName("group_id")
    val groupId: String? = null,

    @SerialName("campaign_id")
    val campaignId: String? = null,

    @SerialName("session_id")
    val sessionId: String? = null,

    @SerialName("device_id")
    val deviceId: String? = null,

    @SerialName("device_manufacturer")
    val deviceManufacturer: String? = null,

    @SerialName("device_model")
    val deviceModel: String? = null,

    @SerialName("device_os_name")
    val deviceOsName: String? = null,

    @SerialName("device_os_version")
    val deviceOsVersion: String? = null,

    @SerialName("app_name")
    val appName: String? = null,

    val provider: String? = null,

    val type: String = "download",

    val timestamp: Instant? = null,

    val duration: Long? = null,

    val scheduled: Boolean? = null,

    val success: Boolean? = null,

    @SerialName("carrier_aggregation")
    val carrierAggregation: Boolean? = null,

    @SerialName("network_connected")
    val networkConnected: Boolean? = null,

    @SerialName("network_available")
    val networkAvailable: Boolean? = null,

    @SerialName("network_roaming")
    val networkRoaming: Boolean? = null,

    @SerialName("data_id")
    var dataId: String? = null,

    @SerialName("latency_data_id")
    var latencyDataId: String? = null,

    @SerialName("created_on")
    val createdOn: Instant,

    @SerialName("updated_on")
    val updatedOn: Instant,

    @SerialName("extra_data")
    val extraData: String? = null,

//    @SerialName("created_on")
//    val createdOn: Long? = null,

//    @SerialName("updated_on")
//    val updatedOn: Long? = null,
)

@Serializable
data class UploadDownloadData(
    val id: String,
    @SerialName("warmup_duration")
    val warmupDuration: Long?,
    @SerialName("warmup_bytes")
    val warmupBytes: Long?,
    @SerialName("duration")
    val duration: Long?,
    @SerialName("bytes")
    val bytes: Long?,
    @SerialName("servers")
    val servers: List<String>,
    @SerialName("created_on")
    val createdOn: Instant,
    @SerialName("updated_on")
    val updatedOn: Instant,
)

@Serializable
data class Location(
    val id: String,
    val timestamp: Instant,
    val lat: Double?,
    val lon: Double?,
    val accuracy: Double?,
    val speed: Double?,
    @SerialName("speed_accuracy")
    val speedAccuracy: Double?,
    val heading: Double?,
    @SerialName("measurement_id")
    val measurementId: String? = null,
    @SerialName("created_on")
    val createdOn: Instant,
    @SerialName("updated_on")
    val updatedOn: Instant,
)

//fun NdtMMeasurementToMeasurement(ndtMeasurement: NdtMMeasurement): Measurement? {
//    if (ndtMeasurement == null) return null
//
//    val measurement = Measurement(
//
//    )
//}