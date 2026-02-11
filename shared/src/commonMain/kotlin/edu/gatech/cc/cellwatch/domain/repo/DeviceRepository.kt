package edu.gatech.cc.cellwatch.domain.repo

import edu.gatech.cc.cellwatch.domain.model.Device
import kotlinx.coroutines.flow.Flow

/**
 * Domain boundary for working with Device data.
 * Platform-agnostic; implemented in the data layer.
 */
interface DeviceRepository {

    /** Create or update the device record. */
    suspend fun upsert(device: Device)

    /** Remove the device by id. */
    suspend fun delete(deviceId: String)

    /** Fetch a single device. */
    suspend fun getById(deviceId: String): Device?

    /** Fetch all devices (you may or may not need this). */
    suspend fun getAll(): List<Device>

    /** Optional: allow UI to observe any changes to the device table. */
    fun observeAll(): Flow<List<Device>>
}