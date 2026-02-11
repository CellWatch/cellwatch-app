package edu.gatech.cc.cellwatch.domain.repo

import edu.gatech.cc.cellwatch.domain.model.Location
import kotlinx.coroutines.flow.Flow

interface LocationRepository {
    suspend fun upsert(location: Location)
    suspend fun delete(id: String)
    suspend fun deleteByMeasurementId(measurementId: String)
    suspend fun getByMeasurementId(measurementId: String): List<Location>
    fun observeByMeasurementId(measurementId: String): Flow<List<Location>>
}
