package edu.gatech.cc.cellwatch.domain.repo

import edu.gatech.cc.cellwatch.domain.model.Cell
import kotlinx.coroutines.flow.Flow

/**
 * Domain boundary for working with Cell data.
 * Platform-agnostic; implemented in the data layer.
 */
interface CellRepository {
    suspend fun upsert(cell: Cell)
    suspend fun delete(id: String)

    suspend fun getById(id: String): Cell?
    suspend fun getByMeasurement(measurementId: String): List<Cell>

    /** Optional streaming API if you want UI to observe changes. */
    fun observeByMeasurement(measurementId: String): Flow<List<Cell>>
}