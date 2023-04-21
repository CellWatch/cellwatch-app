package com.example.ndt8.data.repository

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.example.ndt8.data.entities.LatencyData
import com.example.ndt8.data.entities.Location
import com.example.ndt8.data.entities.Measurement
import com.example.ndt8.data.entities.MeasurementWithAllData
import com.example.ndt8.data.entities.UploadDownloadData
import kotlinx.coroutines.flow.Flow

@Dao
interface MeasurementDao {
    @Insert
    fun insertMeasurement(measurement: Measurement)

    @Insert
    fun insertMeasurementWithLocationsAndData(
        measurement: Measurement,
        locations: List<Location>,
        uploadDownloadData: List<UploadDownloadData>,
        latencyData: List<LatencyData>
    )

//    @Insert
//    fun insertMeasurementWithAllData(measurement: MeasurementWithAllData)

    @Delete
    fun deleteMeasurement(measurement: Measurement)

    @Query("SELECT * FROM Measurement")
    fun getMeasurements(): Flow<List<Measurement>>

    @Transaction
    @Query("SELECT * FROM Measurement")
    fun getMeasurementsWithAllData(): Flow<List<MeasurementWithAllData>>
}