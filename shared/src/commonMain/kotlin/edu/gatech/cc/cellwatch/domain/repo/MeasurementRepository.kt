package edu.gatech.cc.cellwatch.domain.repo

import edu.gatech.cc.cellwatch.domain.model.Measurement
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

interface MeasurementRepository {
    suspend fun upsert(measurement: Measurement)
    suspend fun delete(id: String)
    suspend fun getById(id: String): Measurement?
    suspend fun getByGroupId(groupId: String): List<Measurement>
    suspend fun getUnsynced(): List<Measurement>
    suspend fun markUploaded(id: String, uploadedAt: Instant)
    fun observeByGroupId(groupId: String): Flow<List<Measurement>>
}
