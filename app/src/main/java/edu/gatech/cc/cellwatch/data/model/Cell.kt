package edu.gatech.cc.cellwatch.data.model

import edu.gatech.cc.cellwatch.data.local.model.CellEntity
import edu.gatech.cc.cellwatch.data.network.model.NetworkCell
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class Cell(
    var id: String = UUID.randomUUID().toString(),
    val timestamp: Instant? = null,
    val cellId: Long? = null,
    val physicalCellId: Int? = null,
    val cellConnection: Int? = null,
    val networkGeneration: String? = null,
    val networkSubtype: String? = null,
    val signalStrength: Int? = null,
    val rssi: Int? = null,
    val rsrp: Int? = null,
    val rsrq: Int? = null,
    val sinr: Int? = null,
    val csiRsrp: Int? = null,
    val csiRsrq: Int? = null,
    val csiSinr: Int? = null,
    val cqi: Int? = null,
    val spectrumBand: String? = null,
    val spectrumBandwidth: Float? = null,
    val arfcn: Int? = null,

    var measurementId: String? = null,
    val createdOn: Instant? = null,
    val updatedOn: Instant? = null
)

fun Cell.asEntity() = CellEntity(
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

fun Cell.asNetworkModel() = NetworkCell(
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
