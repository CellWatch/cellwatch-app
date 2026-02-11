package edu.gatech.cc.cellwatch.data.repo

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import edu.gatech.cc.cellwatch.data.mappers.toDomain
import edu.gatech.cc.cellwatch.data.mappers.toRow
import edu.gatech.cc.cellwatch.db.LocationQueries
import edu.gatech.cc.cellwatch.domain.model.Location
import edu.gatech.cc.cellwatch.domain.repo.LocationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.coroutines.CoroutineContext

class LocationRepositoryImpl(
    private val queries: LocationQueries,
    private val io: CoroutineContext,
) : LocationRepository {

    override suspend fun upsert(location: Location) {
        val row = location.toRow()
        queries.insertOrReplaceLocation(
            id = row.id,
            timestamp = row.timestamp,
            lat = row.lat,
            lon = row.lon,
            accuracy = row.accuracy,
            speed = row.speed,
            speedAccuracy = row.speedAccuracy,
            heading = row.heading,
            measurementId = row.measurementId,
            createdOn = row.createdOn,
            updatedOn = row.updatedOn,
        )
    }

    override suspend fun delete(id: String) {
        queries.deleteLocationById(id)
    }

    override suspend fun deleteByMeasurementId(measurementId: String) {
        queries.deleteLocationsByMeasurement(measurementId)
    }

    override suspend fun getByMeasurementId(measurementId: String): List<Location> =
        queries.selectLocationsByMeasurement(measurementId).executeAsList().map { it.toDomain() }

    override fun observeByMeasurementId(measurementId: String): Flow<List<Location>> =
        queries.selectLocationsByMeasurement(measurementId)
            .asFlow()
            .mapToList(io)
            .map { rows -> rows.map { it.toDomain() } }
}
