package edu.gatech.cc.cellwatch.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import edu.gatech.cc.cellwatch.data.local.model.LatencyDataEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LatencyDataDao {
    @Insert
    suspend fun insertLatencyData(measurement: LatencyDataEntity)

    @Delete
    suspend fun deleteLatencyData(measurement: LatencyDataEntity)

    @Query("SELECT * FROM LatencyDataEntity")
    fun getLatencyDataFlow(): Flow<List<LatencyDataEntity>>

    @Query("SELECT * FROM LatencyDataEntity WHERE measurementId = :measurementId")
    fun getLatencyDataByMeasurementIdFlow(measurementId: String): Flow<List<LatencyDataEntity>>
}
