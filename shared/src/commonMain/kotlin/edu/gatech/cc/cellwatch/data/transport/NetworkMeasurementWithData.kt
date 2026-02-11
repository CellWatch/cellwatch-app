package edu.gatech.cc.cellwatch.data.transport

import edu.gatech.cc.cellwatch.domain.model.Measurement
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
    val cells: List<NetworkCell>?,
)

fun Measurement.toNetworkWithData(): NetworkMeasurementWithData = NetworkMeasurementWithData(
    measurement = toNetwork().copy(
        uploadDownloadData = null,
        latencyData = null,
        locations = null,
        cells = null,
    ),
    measurementData = uploadDownloadData?.toNetwork(),
    latencyData = latencyData?.toNetwork(),
    locations = locations?.map { it.toNetwork() },
    cells = cells?.map { it.toNetwork() },
)

fun NetworkMeasurementWithData.toDomain(): Measurement = measurement.toDomain().copy(
    uploadDownloadData = measurementData?.toDomain(),
    latencyData = latencyData?.toDomain(),
    locations = locations?.mapNotNull { it.toDomainOrNull() },
    cells = cells?.map { it.toDomain() },
)
