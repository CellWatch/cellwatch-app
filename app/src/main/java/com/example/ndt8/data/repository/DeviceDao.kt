package com.example.ndt8.data.repository

import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.example.ndt8.data.entities.Device

interface DeviceDao {
    @Insert
    fun insertDevice(device: Device)

    @Delete
    fun deleteDevice(device: Device)

    @Query("SELECT * FROM Device")
    fun getDevices(): List<Device>
}