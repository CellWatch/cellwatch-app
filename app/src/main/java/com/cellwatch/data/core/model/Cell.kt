package com.cellwatch.data.core.model

import com.cellwatch.data.local.model.CellEntity
import com.cellwatch.data.network.model.NetworkCell

fun NetworkCell.asEntity() = CellEntity(
    id, timestamp, cellId, physicalCellId, cellConnection, networkGeneration, networkSubtype, signalStrength, rssi, rsrp, rsrq, sinr, csiRsrp, csiRsrq, csiSinr, cqi, spectrumBand, spectrumBandwidth, arfcn, measurementId, createdOn, updatedOn
)

fun CellEntity.asNetworkModel() = NetworkCell(
    id, timestamp, cellId, physicalCellId, cellConnection, networkGeneration, networkSubtype, signalStrength, rssi, rsrp, rsrq, sinr, csiRsrp, csiRsrq, csiSinr, cqi, spectrumBand, spectrumBandwidth, arfcn, measurementId, createdOn, updatedOn
)