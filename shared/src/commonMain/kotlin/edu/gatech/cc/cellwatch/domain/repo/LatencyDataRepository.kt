package edu.gatech.cc.cellwatch.domain.repo

import edu.gatech.cc.cellwatch.domain.model.LatencyData
import kotlinx.coroutines.flow.Flow

interface LatencyDataRepository {
    suspend fun upsert(latencyData: LatencyData)
    suspend fun delete(id: String)
    suspend fun deleteByMeasurementId(measurementId: String)
    suspend fun getByMeasurementId(measurementId: String): List<LatencyData>
    fun observeByMeasurementId(measurementId: String): Flow<List<LatencyData>>
}
