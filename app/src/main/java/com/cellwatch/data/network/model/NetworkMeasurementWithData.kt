package com.cellwatch.data.network.model

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