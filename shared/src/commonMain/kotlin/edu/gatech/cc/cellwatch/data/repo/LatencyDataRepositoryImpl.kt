package edu.gatech.cc.cellwatch.data.repo

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import edu.gatech.cc.cellwatch.data.mappers.toDomain
import edu.gatech.cc.cellwatch.data.mappers.toRow
import edu.gatech.cc.cellwatch.db.LatencyDataQueries
import edu.gatech.cc.cellwatch.domain.model.LatencyData
import edu.gatech.cc.cellwatch.domain.repo.LatencyDataRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.coroutines.CoroutineContext

class LatencyDataRepositoryImpl(
    private val queries: LatencyDataQueries,
    private val io: CoroutineContext,
) : LatencyDataRepository {

    override suspend fun upsert(latencyData: LatencyData) {
        val row = latencyData.toRow()
        queries.insertOrReplaceLatencyData(
            id = row.id,
            measurementId = row.measurementId,
            rtt = row.rtt,
            jitter = row.jitter,
            sent = row.sent,
            received = row.received,
            servers = row.servers,
            createdOn = row.createdOn,
            updatedOn = row.updatedOn,
        )
    }

    override suspend fun delete(id: String) {
        queries.deleteLatencyDataById(id)
    }

    override suspend fun deleteByMeasurementId(measurementId: String) {
        queries.deleteLatencyDataByMeasurement(measurementId)
    }

    override suspend fun getByMeasurementId(measurementId: String): List<LatencyData> =
        queries.selectLatencyDataByMeasurement(measurementId).executeAsList().map { it.toDomain() }

    override fun observeByMeasurementId(measurementId: String): Flow<List<LatencyData>> =
        queries.selectLatencyDataByMeasurement(measurementId)
            .asFlow()
            .mapToList(io)
            .map { rows -> rows.map { it.toDomain() } }
}
