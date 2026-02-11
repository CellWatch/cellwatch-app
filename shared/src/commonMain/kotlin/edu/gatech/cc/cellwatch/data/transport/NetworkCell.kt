package edu.gatech.cc.cellwatch.data.transport

import edu.gatech.cc.cellwatch.domain.model.Cell
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NetworkCell(
    var id: String,
    val timestamp: Instant? = null,
    @SerialName("cell_id")
    val cellId: Long? = null,
    @SerialName("physical_cell_id")
    val physicalCellId: Int? = null,
    @SerialName("cell_connection")
    val cellConnection: Int? = null,
    @SerialName("network_generation")
    val networkGeneration: String? = null,
    @SerialName("network_subtype")
    val networkSubtype: String? = null,
    @SerialName("signal_strength")
    val signalStrength: Int? = null,
    val rssi: Int? = null,
    val rsrp: Int? = null,
    val rsrq: Int? = null,
    val sinr: Int? = null,
    @SerialName("csi_rsrp")
    val csiRsrp: Int? = null,
    @SerialName("csi_rsrq")
    val csiRsrq: Int? = null,
    @SerialName("csi_sinr")
    val csiSinr: Int? = null,
    val cqi: Int? = null,
    @SerialName("spectrum_band")
    val spectrumBand: String? = null,
    @SerialName("spectrum_bandwidth")
    val spectrumBandwidth: Float? = null,
    val arfcn: Int? = null,
    @SerialName("measurement_id")
    var measurementId: String? = null,
    @SerialName("created_on")
    val createdOn: Instant? = null,
    @SerialName("updated_on")
    val updatedOn: Instant? = null,
)

fun Cell.toNetwork(): NetworkCell = NetworkCell(
    id = id,
    timestamp = timestamp,
    cellId = cellId,
    physicalCellId = physicalCellId,
    cellConnection = cellConnection,
    networkGeneration = networkGeneration,
    networkSubtype = networkSubtype,
    signalStrength = signalStrength,
    rssi = rssi,
    rsrp = rsrp,
    rsrq = rsrq,
    sinr = sinr,
    csiRsrp = csiRsrp,
    csiRsrq = csiRsrq,
    csiSinr = csiSinr,
    cqi = cqi,
    spectrumBand = spectrumBand,
    spectrumBandwidth = spectrumBandwidth,
    arfcn = arfcn,
    measurementId = measurementId,
    createdOn = createdOn,
    updatedOn = updatedOn,
)

fun NetworkCell.toDomain(): Cell = Cell(
    id = id,
    timestamp = timestamp,
    cellId = cellId,
    physicalCellId = physicalCellId,
    cellConnection = cellConnection,
    networkGeneration = networkGeneration,
    networkSubtype = networkSubtype,
    signalStrength = signalStrength,
    rssi = rssi,
    rsrp = rsrp,
    rsrq = rsrq,
    sinr = sinr,
    csiRsrp = csiRsrp,
    csiRsrq = csiRsrq,
    csiSinr = csiSinr,
    cqi = cqi,
    spectrumBand = spectrumBand,
    spectrumBandwidth = spectrumBandwidth,
    arfcn = arfcn,
    measurementId = measurementId,
    createdOn = createdOn,
    updatedOn = updatedOn,
)
