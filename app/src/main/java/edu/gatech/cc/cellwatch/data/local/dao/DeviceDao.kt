package edu.gatech.cc.cellwatch.data.local.dao

import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import edu.gatech.cc.cellwatch.data.local.model.DeviceEntity

interface DeviceDao {
    @Insert
    suspend fun insertDevice(deviceEntity: DeviceEntity)

    @Delete
    suspend fun deleteDevice(deviceEntity: DeviceEntity)

    @Query("SELECT * FROM DeviceEntity")
    suspend fun getDevices(): List<DeviceEntity>
}
