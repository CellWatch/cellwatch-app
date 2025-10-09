package edu.gatech.cc.cellwatch.data.mappers

// This file defines mapping functions between SQLDelight-generated database entities
// and the platform-independent domain model used throughout the app. It converts primitive
// SQL storage types (Long, Double, String, etc.) into domain-layer Kotlin types such as
// Instant, Int, or Float, ensuring the database layer remains platform-agnostic.

import edu.gatech.cc.cellwatch.domain.model.Cell
import edu.gatech.cc.cellwatch.db.CellEntity
import kotlinx.datetime.Instant

// Converts a SQLDelight CellEntity (database row) into a domain-level Cell object.
// Handles type conversions like Long → Instant or Long → Int.
fun CellEntity.toDomain(): Cell = Cell(
    id = id,
    timestamp = timestamp?.let(Instant::fromEpochMilliseconds),

    cellId = cellId,
    physicalCellId = physicalCellId?.toInt(),
    cellConnection = cellConnection?.toInt(),
    networkGeneration = networkGeneration,
    networkSubtype = networkSubtype,
    signalStrength = signalStrength?.toInt(),
    rssi = rssi?.toInt(),
    rsrp = rsrp?.toInt(),
    rsrq = rsrq?.toInt(),
    sinr = sinr?.toInt(),
    csiRsrp = csiRsrp?.toInt(),
    csiRsrq = csiRsrq?.toInt(),
    csiSinr = csiSinr?.toInt(),
    cqi = cqi?.toInt(),
    spectrumBand = spectrumBand,
    spectrumBandwidth = spectrumBandwidth?.toFloat(),
    arfcn = arfcn?.toInt(),

    measurementId = measurementId,
    createdOn = createdOn?.let(Instant::fromEpochMilliseconds),
    updatedOn = updatedOn?.let(Instant::fromEpochMilliseconds)
)

// Converts a domain-level Cell model into a SQLDelight-compatible CellEntity for persistence.
// Performs inverse conversions like Instant → Long or Int → Long.
fun Cell.toRow(): CellEntity = CellEntity(
    id = id,
    timestamp = timestamp?.toEpochMilliseconds(),

    cellId = cellId,
    physicalCellId = physicalCellId?.toLong(),
    cellConnection = cellConnection?.toLong(),
    networkGeneration = networkGeneration,
    networkSubtype = networkSubtype,
    signalStrength = signalStrength?.toLong(),
    rssi = rssi?.toLong(),
    rsrp = rsrp?.toLong(),
    rsrq = rsrq?.toLong(),
    sinr = sinr?.toLong(),
    csiRsrp = csiRsrp?.toLong(),
    csiRsrq = csiRsrq?.toLong(),
    csiSinr = csiSinr?.toLong(),
    cqi = cqi?.toLong(),
    spectrumBand = spectrumBand,
    spectrumBandwidth = spectrumBandwidth?.toDouble(),
    arfcn = arfcn?.toLong(),

    measurementId = measurementId,
    createdOn = createdOn?.toEpochMilliseconds(),
    updatedOn = updatedOn?.toEpochMilliseconds()
)