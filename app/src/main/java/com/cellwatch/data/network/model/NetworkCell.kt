package com.cellwatch.data.network.model

import com.cellwatch.data.model.Cell
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class NetworkCell(
    var id: String = UUID.randomUUID().toString(),
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
    val updatedOn: Instant? = null
)

fun NetworkCell.asExternalModel() = Cell(
    id,
    timestamp,
    cellId,
    physicalCellId,
    cellConnection,
    networkGeneration,
    networkSubtype,
    signalStrength,
    rssi,
    rsrp,
    rsrq,
    sinr,
    csiRsrp,
    csiRsrq,
    csiSinr,
    cqi,
    spectrumBand,
    spectrumBandwidth,
    arfcn,
    measurementId,
    createdOn,
    updatedOn
)