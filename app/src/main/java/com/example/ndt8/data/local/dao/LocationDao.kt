package com.example.ndt8.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.example.ndt8.data.local.model.LocationEntity

@Dao
interface LocationDao {
    @Insert
    suspend fun insertLocation(measurement: LocationEntity)

    @Delete
    suspend fun deleteLocation(measurement: LocationEntity)

    @Query("SELECT * FROM LocationEntity")
    suspend fun getLocations(): List<LocationEntity>
}