package edu.gatech.cc.cellwatch.data.network.model

import edu.gatech.cc.cellwatch.data.model.Measurement
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NetworkMeasurementWithData(
    @SerialName("in_measurement")
    val measurement: NetworkMeasurement,
    @SerialName("in_measurement_data")
    val measurementData: NetworkUploadDownloadData?,
    @SerialName("in_latency_data")
    val latencyData: NetworkLatencyData?,
    @SerialName("in_locations")
    val locations: List<NetworkLocation>?,
    @SerialName("in_cells")
    val cells: List<NetworkCell>?
)

fun NetworkMeasurementWithData.asExternalModel() = Measurement(
    measurement.id,
    measurement.groupId,
    measurement.campaignId,
    measurement.sessionId,
    measurement.deviceId,
    measurement.deviceManufacturer,
    measurement.deviceModel,
    measurement.deviceOsName,
    measurement.deviceOsVersion,
    measurement.appName,
    measurement.provider,
    measurement.type,
    measurement.timestamp,
    measurement.duration,
    measurement.scheduled,
    measurement.success,
    measurement.carrierAggregation,
    measurement.networkConnected,
    measurement.networkAvailable,
    measurement.networkRoaming,
    measurement.extraData,
    measurement.simMcc,
    measurement.simMnc,
    measurement.netMcc,
    measurement.netMnc,
    measurement.createdOn,
    measurement.updatedOn,
    measurementData?.asExternalModel(),
    latencyData?.asExternalModel(),
    locations?.map { location -> location.asExternalModel() },
    cells?.map { cell -> cell.asExternalModel() },
    measurement.connectionType,
    measurement.cellularDataEnabled,
)
