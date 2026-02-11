package edu.gatech.cc.cellwatch.data.transport

import com.benasher44.uuid.uuid4
import edu.gatech.cc.cellwatch.domain.model.Measurement
import edu.gatech.cc.cellwatch.domain.model.NetworkConnectionType
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NetworkMeasurement(
    var id: String = uuid4().toString(),
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
    val connectionType: NetworkConnectionType? = null,
    @SerialName("cellular_data_enabled")
    val cellularDataEnabled: Boolean? = null,
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
    var cells: List<NetworkCell>? = null,
    @SerialName("app_version")
    val appVersion: String? = null,
)

fun Measurement.toNetwork(): NetworkMeasurement = NetworkMeasurement(
    id = id,
    groupId = groupId,
    campaignId = campaignId,
    sessionId = sessionId,
    deviceId = deviceId,
    deviceManufacturer = deviceManufacturer,
    deviceModel = deviceModel,
    deviceOsName = deviceOsName,
    deviceOsVersion = deviceOsVersion,
    appName = appName,
    provider = provider,
    type = type,
    timestamp = timestamp,
    duration = duration,
    scheduled = scheduled,
    success = success,
    carrierAggregation = carrierAggregation,
    networkConnected = networkConnected,
    networkAvailable = networkAvailable,
    networkRoaming = networkRoaming,
    simMcc = simMcc,
    simMnc = simMnc,
    netMcc = netMcc,
    netMnc = netMnc,
    connectionType = connectionType,
    cellularDataEnabled = cellularDataEnabled,
    extraData = extraData,
    createdOn = createdOn,
    updatedOn = updatedOn,
    uploadDownloadData = uploadDownloadData?.toNetwork(),
    latencyData = latencyData?.toNetwork(),
    locations = locations?.map { it.toNetwork() },
    cells = cells?.map { it.toNetwork() },
    appVersion = appVersion,
)

fun NetworkMeasurement.toDomain(): Measurement = Measurement(
    id = id,
    groupId = groupId,
    campaignId = campaignId,
    sessionId = sessionId,
    deviceId = deviceId,
    deviceManufacturer = deviceManufacturer,
    deviceModel = deviceModel,
    deviceOsName = deviceOsName,
    deviceOsVersion = deviceOsVersion,
    appName = appName,
    provider = provider,
    type = type,
    timestamp = timestamp,
    duration = duration,
    scheduled = scheduled,
    success = success,
    carrierAggregation = carrierAggregation,
    networkConnected = networkConnected,
    networkAvailable = networkAvailable,
    networkRoaming = networkRoaming,
    simMcc = simMcc,
    simMnc = simMnc,
    netMcc = netMcc,
    netMnc = netMnc,
    extraData = extraData,
    createdOn = createdOn,
    updatedOn = updatedOn,
    uploadDownloadData = uploadDownloadData?.toDomain(),
    latencyData = latencyData?.toDomain(),
    locations = locations?.mapNotNull { it.toDomainOrNull() },
    cells = cells?.map { it.toDomain() },
    connectionType = connectionType,
    cellularDataEnabled = cellularDataEnabled,
    appVersion = appVersion,
)
