package edu.gatech.cc.cellwatch.data.network.model

import edu.gatech.cc.cellwatch.data.model.Measurement
import edu.gatech.cc.cellwatch.domain.telephony.managers.NetworkConnectionType
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
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

    @SerialName("sim_mobile_country_code")
    val simMcc: String? = null,

    @SerialName("sim_mobile_network_code")
    val simMnc: String? = null,

    @SerialName("net_mobile_country_code")
    val netMcc: String? = null,

    @SerialName("net_mobile_network_code")
    val netMnc: String? = null,

    @SerialName("connection_type")
    val connectionType: NetworkConnectionType?,

    @SerialName("cellular_data_enabled")
    val cellularDataEnabled: Boolean?,

    @SerialName("extra_data")
    val extraData: String? = null,

    @SerialName("created_on")
    val createdOn: Instant? = null,

    @SerialName("updated_on")
    val updatedOn: Instant? = null,

    @SerialName("upload_download_data")
    var uploadDownloadData: NetworkUploadDownloadData? = null,

    @SerialName("latency_data")
    var latencyData: NetworkLatencyData? = null,

    var locations: List<NetworkLocation>? = null,

    var cells: List<NetworkCell>? = null
)

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
    simMcc,
    simMnc,
    netMcc,
    netMnc,
    extraData,
    createdOn,
    updatedOn,
    uploadDownloadData?.asExternalModel(),
    latencyData?.asExternalModel(),
    locations?.map { location -> location.asExternalModel() },
    cells?.map { cell -> cell.asExternalModel() },
    connectionType,
    cellularDataEnabled,
)
