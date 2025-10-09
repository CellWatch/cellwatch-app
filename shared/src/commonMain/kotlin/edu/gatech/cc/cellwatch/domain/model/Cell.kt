package edu.gatech.cc.cellwatch.domain.model

import kotlinx.datetime.Instant

/**
 * Domain entity representing one cellular measurement sample.
 * Pure Kotlin (no Android or SQL types), suitable for KMP.
 */
data class Cell(
    val id: String,
    val timestamp: Instant?,

    val cellId: Long?,
    val physicalCellId: Int?,
    val cellConnection: Int?,
    val networkGeneration: String?,
    val networkSubtype: String?,
    val signalStrength: Int?,
    val rssi: Int?,
    val rsrp: Int?,
    val rsrq: Int?,
    val sinr: Int?,
    val csiRsrp: Int?,
    val csiRsrq: Int?,
    val csiSinr: Int?,
    val cqi: Int?,
    val spectrumBand: String?,
    val spectrumBandwidth: Float?,
    val arfcn: Int?,

    val measurementId: String?,   // foreign key to Measurement in your model
    val createdOn: Instant?,
    val updatedOn: Instant?
)