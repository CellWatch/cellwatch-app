package com.example.ndt8.data.repository

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.example.ndt8.data.entities.Location

@Dao
interface LocationDao {
    @Insert
    fun insertLocation(measurement: Location)

    @Delete
    fun deleteLocation(measurement: Location)

    @Query("SELECT * FROM Location")
    fun getLocations(): List<Location>
}