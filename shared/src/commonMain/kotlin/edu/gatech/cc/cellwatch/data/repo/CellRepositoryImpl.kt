package edu.gatech.cc.cellwatch.data.repo

import edu.gatech.cc.cellwatch.domain.model.Cell
import edu.gatech.cc.cellwatch.domain.repo.CellRepository
import edu.gatech.cc.cellwatch.db.CellQueries
import edu.gatech.cc.cellwatch.data.mappers.toDomain
import edu.gatech.cc.cellwatch.data.mappers.toRow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlin.coroutines.CoroutineContext

class CellRepositoryImpl(
    private val queries: CellQueries,
    private val io: CoroutineContext
) : CellRepository {

    override suspend fun upsert(cell: Cell) {
        val row = cell.toRow()
        queries.insertOrReplaceCell(
            id = row.id,
            timestamp = row.timestamp,
            cellId = row.cellId,
            physicalCellId = row.physicalCellId,
            cellConnection = row.cellConnection,
            networkGeneration = row.networkGeneration,
            networkSubtype = row.networkSubtype,
            signalStrength = row.signalStrength,
            rssi = row.rssi,
            rsrp = row.rsrp,
            rsrq = row.rsrq,
            sinr = row.sinr,
            csiRsrp = row.csiRsrp,
            csiRsrq = row.csiRsrq,
            csiSinr = row.csiSinr,
            cqi = row.cqi,
            spectrumBand = row.spectrumBand,
            spectrumBandwidth = row.spectrumBandwidth,
            arfcn = row.arfcn,
            measurementId = row.measurementId,
            createdOn = row.createdOn,
            updatedOn = row.updatedOn
        )
    }

    override suspend fun delete(id: String) {
        queries.deleteCellById(id)
    }

    override suspend fun getById(id: String): Cell? =
        queries.selectCellById(id).executeAsOneOrNull()?.toDomain()

    override suspend fun getByMeasurement(measurementId: String): List<Cell> =
        queries.selectCellsByMeasurement(measurementId).executeAsList().map { it.toDomain() }

    override fun observeByMeasurement(measurementId: String): Flow<List<Cell>> =
        queries.selectCellsByMeasurement(measurementId)
            .asFlow()
            .mapToList(io)
            .map { rows -> rows.map { it.toDomain() } }
}