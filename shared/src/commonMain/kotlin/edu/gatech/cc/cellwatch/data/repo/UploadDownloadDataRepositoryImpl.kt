package edu.gatech.cc.cellwatch.data.repo

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import edu.gatech.cc.cellwatch.data.mappers.toDomain
import edu.gatech.cc.cellwatch.data.mappers.toRow
import edu.gatech.cc.cellwatch.db.UploadDownloadDataQueries
import edu.gatech.cc.cellwatch.domain.model.UploadDownloadData
import edu.gatech.cc.cellwatch.domain.repo.UploadDownloadDataRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.coroutines.CoroutineContext

class UploadDownloadDataRepositoryImpl(
    private val queries: UploadDownloadDataQueries,
    private val io: CoroutineContext,
) : UploadDownloadDataRepository {

    override suspend fun upsert(uploadDownloadData: UploadDownloadData) {
        val row = uploadDownloadData.toRow()
        queries.insertOrReplaceUploadDownloadData(
            id = row.id,
            measurementId = row.measurementId,
            warmupDuration = row.warmupDuration,
            warmupBytes = row.warmupBytes,
            duration = row.duration,
            bytes = row.bytes,
            bytesPerSec = row.bytesPerSec,
            applicationBytes = row.applicationBytes,
            applicationBytesPerSec = row.applicationBytesPerSec,
            servers = row.servers,
            createdOn = row.createdOn,
            updatedOn = row.updatedOn,
        )
    }

    override suspend fun delete(id: String) {
        queries.deleteUploadDownloadDataById(id)
    }

    override suspend fun deleteByMeasurementId(measurementId: String) {
        queries.deleteUploadDownloadDataByMeasurement(measurementId)
    }

    override suspend fun getByMeasurementId(measurementId: String): List<UploadDownloadData> =
        queries.selectUploadDownloadDataByMeasurement(measurementId).executeAsList().map { it.toDomain() }

    override fun observeByMeasurementId(measurementId: String): Flow<List<UploadDownloadData>> =
        queries.selectUploadDownloadDataByMeasurement(measurementId)
            .asFlow()
            .mapToList(io)
            .map { rows -> rows.map { it.toDomain() } }
}
