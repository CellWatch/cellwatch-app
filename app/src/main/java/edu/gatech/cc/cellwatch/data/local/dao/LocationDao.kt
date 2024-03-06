package edu.gatech.cc.cellwatch.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import edu.gatech.cc.cellwatch.data.local.model.LocationEntity

@Dao
interface LocationDao {
    @Insert
    suspend fun insertLocation(measurement: LocationEntity)

    @Delete
    suspend fun deleteLocation(measurement: LocationEntity)
}
