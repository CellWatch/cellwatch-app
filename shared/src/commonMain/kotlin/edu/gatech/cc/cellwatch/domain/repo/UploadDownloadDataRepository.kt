package edu.gatech.cc.cellwatch.domain.repo

import edu.gatech.cc.cellwatch.domain.model.UploadDownloadData
import kotlinx.coroutines.flow.Flow

interface UploadDownloadDataRepository {
    suspend fun upsert(uploadDownloadData: UploadDownloadData)
    suspend fun delete(id: String)
    suspend fun deleteByMeasurementId(measurementId: String)
    suspend fun getByMeasurementId(measurementId: String): List<UploadDownloadData>
    fun observeByMeasurementId(measurementId: String): Flow<List<UploadDownloadData>>
}
