package edu.gatech.cc.cellwatch.data.repo

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import edu.gatech.cc.cellwatch.data.mappers.toDomain
import edu.gatech.cc.cellwatch.data.mappers.toRow
import edu.gatech.cc.cellwatch.db.MeasurementQueries
import edu.gatech.cc.cellwatch.domain.model.Measurement
import edu.gatech.cc.cellwatch.domain.repo.MeasurementRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant
import kotlin.coroutines.CoroutineContext

class MeasurementRepositoryImpl(
    private val queries: MeasurementQueries,
    private val io: CoroutineContext,
) : MeasurementRepository {

    override suspend fun upsert(measurement: Measurement) {
        val row = measurement.toRow()
        queries.insertOrReplaceMeasurement(
            id = row.id,
            groupId = row.groupId,
            campaignId = row.campaignId,
            sessionId = row.sessionId,
            deviceId = row.deviceId,
            deviceManufacturer = row.deviceManufacturer,
            deviceModel = row.deviceModel,
            deviceOsName = row.deviceOsName,
            deviceOsVersion = row.deviceOsVersion,
            appName = row.appName,
            provider = row.provider,
            type = row.type,
            timestamp = row.timestamp,
            duration = row.duration,
            scheduled = row.scheduled,
            success = row.success,
            carrierAggregation = row.carrierAggregation,
            networkConnected = row.networkConnected,
            networkAvailable = row.networkAvailable,
            networkRoaming = row.networkRoaming,
            simMcc = row.simMcc,
            simMnc = row.simMnc,
            netMcc = row.netMcc,
            netMnc = row.netMnc,
            telephonySupport = row.telephonySupport,
            networkSupport = row.networkSupport,
            locationSupport = row.locationSupport,
            deviceSupport = row.deviceSupport,
            capabilityNotes = row.capabilityNotes,
            connectionType = row.connectionType,
            cellularDataEnabled = row.cellularDataEnabled,
            extraData = row.extraData,
            createdOn = row.createdOn,
            updatedOn = row.updatedOn,
            uploadTime = row.uploadTime,
            appVersion = row.appVersion,
        )
    }

    override suspend fun delete(id: String) {
        queries.deleteMeasurementById(id)
    }

    override suspend fun getById(id: String): Measurement? =
        queries.selectMeasurementById(id).executeAsOneOrNull()?.toDomain()

    override suspend fun getByGroupId(groupId: String): List<Measurement> =
        queries.selectMeasurementsByGroupId(groupId).executeAsList().map { it.toDomain() }

    override suspend fun getUnsynced(): List<Measurement> =
        queries.selectUnsyncedMeasurements().executeAsList().map { it.toDomain() }

    override suspend fun markUploaded(id: String, uploadedAt: Instant) {
        queries.markMeasurementUploaded(
            uploadTime = uploadedAt.toEpochMilliseconds(),
            id = id,
        )
    }

    override fun observeByGroupId(groupId: String): Flow<List<Measurement>> =
        queries.selectMeasurementsByGroupId(groupId)
            .asFlow()
            .mapToList(io)
            .map { rows -> rows.map { it.toDomain() } }
}
