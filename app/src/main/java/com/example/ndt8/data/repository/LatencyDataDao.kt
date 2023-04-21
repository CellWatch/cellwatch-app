package com.example.ndt8.data.repository

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.example.ndt8.data.entities.LatencyData

@Dao
interface LatencyDataDao {
    @Insert
    fun insertLatencyData(measurement: LatencyData)

    @Delete
    fun deleteLatencyData(measurement: LatencyData)

    @Query("SELECT * FROM LatencyData")
    fun getLatencyData(): List<LatencyData>
}