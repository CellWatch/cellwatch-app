package com.example.ndt8.data.repository

import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.example.ndt8.data.entities.Measurement
import com.example.ndt8.data.entities.MeasurementWithAllData

interface MeasurementDao {
    @Insert
    fun insertMeasurement(measurement: Measurement)

    @Delete
    fun deleteMeasurement(measurement: Measurement)

    @Query("SELECT * FROM Measurement")
    fun getMeasurements(): List<Measurement>

    @Transaction
    @Query("SELECT * FROM Measurement")
    fun getMeasurementsWithAllData(): List<MeasurementWithAllData>
}