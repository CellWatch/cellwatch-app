package edu.gatech.cc.cellwatch.data.repo

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import edu.gatech.cc.cellwatch.data.mappers.toDomain
import edu.gatech.cc.cellwatch.data.mappers.toRow
import edu.gatech.cc.cellwatch.db.DeviceQueries
import edu.gatech.cc.cellwatch.domain.model.Device
import edu.gatech.cc.cellwatch.domain.repo.DeviceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.coroutines.CoroutineContext

class DeviceRepositoryImpl(
    private val queries: DeviceQueries,
    private val io: CoroutineContext
) : DeviceRepository {

    override suspend fun upsert(device: Device) {
        val row = device.toRow()
        queries.insertDevice(
            deviceId = row.deviceId,
            deviceManufacturer = row.deviceManufacturer,
            deviceModel = row.deviceModel,
            deviceOsName = row.deviceOsName,
            deviceOsVersion = row.deviceOsVersion,
            appName = row.appName
        )
    }

    override suspend fun delete(deviceId: String) {
        queries.deleteDeviceById(deviceId)
    }

    override suspend fun getById(deviceId: String): Device? =
        queries.getDeviceById(deviceId).executeAsOneOrNull()?.toDomain()

    override suspend fun getAll(): List<Device> =
        queries.selectAllDevices().executeAsList().map { it.toDomain() }

    override fun observeAll(): Flow<List<Device>> =
        queries.selectAllDevices()
            .asFlow()
            .mapToList(io)
            .map { rows -> rows.map { it.toDomain() } }
}