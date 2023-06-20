package com.cellwatch.data.network.model

import com.cellwatch.data.model.Measurement
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.util.UUID

/**
 * Network representation of [Measurement] when posted to Supabase
 */
@Serializable
data class NetworkMeasurement(
    var id: String = UUID.randomUUID().toString(),

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

    val type: String,

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

    @SerialName("extra_data")
    val extraData: String? = null,

//    @Transient
    @SerialName("created_on")
    val createdOn: Instant? = null,

//    @Transient
    @SerialName("updated_on")
    val updatedOn: Instant? = null
)

//@Serializable
//data class NetworkLocations(
//    val locations: List<NetworkLocation>
//)

//@Serializable
//data class NetworkMeasurementData(
//    @SerialName("in_measurement")
//    val measurement: NetworkMeasurement,
//    @SerialName("in_measurement_data")
//    val measurementData: NetworkUploadDownloadData,
//    @SerialName("in_locations")
//    val locations: List<NetworkLocation>
//)

fun NetworkMeasurement.asExternalModel() = Measurement(
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