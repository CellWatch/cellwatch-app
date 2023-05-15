package com.example.ndt8.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.ndt8.data.local.model.LatencyDataEntity
import com.example.ndt8.data.local.model.LocationEntity
import com.example.ndt8.data.local.model.MeasurementEntity
import com.example.ndt8.data.local.model.MeasurementWithData
import com.example.ndt8.data.local.model.UploadDownloadDataEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class MeasurementDao {
    @Insert
    abstract suspend fun insertMeasurement(measurement: MeasurementEntity)

    @Transaction
    open suspend fun insertMeasurementWithData(measurementWithData: MeasurementWithData) {
        insertMeasurement(measurementWithData.measurement)

        with(measurementWithData) {
            if (uploadDownloadData != null) {
                uploadDownloadData.measurementId = measurement.id
                insertUploadDownloadData(uploadDownloadData)
            }
            if (latencyData != null) {
                latencyData.measurementId = measurement.id
                insertLatencyData(latencyData)
            }
            locations?.forEach {
                it.measurementId = measurement.id
                insertLocation(it)
            }
        }
    }

    @Insert
    abstract suspend fun insertUploadDownloadData(uploadDownloadDataEntity: UploadDownloadDataEntity)

    @Insert
    abstract suspend fun insertLatencyData(latencyDataEntity: LatencyDataEntity)

    @Insert
    abstract suspend fun insertLocation(locationEntity: LocationEntity)

    @Insert
    suspend fun addUploadDownloadDataToMeasurement(measurement: MeasurementEntity, uploadDownloadDataEntity: UploadDownloadDataEntity) {
        uploadDownloadDataEntity.measurementId = measurement.id

    }

    @Update
    abstract suspend fun updateMeasurement(measurement: MeasurementEntity)

//    @Transaction
//    suspend fun insertMeasurementWithLocationsAndData(
//        measurement: Measurement,
//        locations: List<Location>,
//        uploadDownloadData: UploadDownloadData?,
//        latencyData: LatencyData?
//    ) {
//        insertMeasurement(measurement)
//
//        locations.forEach {
//            it.
//        }
//    }

//    @Insert
//    suspend fun insertMeasurementWithLocationsAndData(
//        measurement: Measurement,
//        locations: List<Location>,
//        uploadDownloadData: UploadDownloadData?,
//        latencyData: LatencyData?
////        latencyData: List<LatencyData>
//    )

//    @Insert
//    fun insertMeasurementWithData(measurement: MeasurementWithData)

    @Delete
    abstract suspend fun deleteMeasurement(measurement: MeasurementEntity)

    @Query("DELETE FROM MeasurementEntity")
    abstract suspend fun deleteAllMeasurements()

    @Query("SELECT * FROM MeasurementEntity WHERE id = :id")
    abstract suspend fun getMeasurementById(id: String): MeasurementEntity

//    @Query("SELECT * FROM Measurement WHERE id = :id")
//    abstract fun getMeasurementByIdFlow(id: String): Flow<List<Measurement>>

    @Query("SELECT * FROM MeasurementEntity")
    abstract suspend fun getMeasurements(): List<MeasurementEntity>

    @Query("SELECT * FROM MeasurementEntity WHERE isSynchronized = 0")
    abstract suspend fun getUnsynchronizedMeasurements(): List<MeasurementEntity>

    @Query("SELECT * FROM MeasurementEntity")
    abstract fun getMeasurementsFlow(): Flow<List<MeasurementEntity>>

    @Transaction
    @Query("SELECT * FROM MeasurementEntity")
    abstract suspend fun getMeasurementsWithData(): List<MeasurementWithData>

    @Transaction
    @Query("SELECT * FROM MeasurementEntity WHERE isSynchronized = 0")
    abstract suspend fun getUnsynchronizedMeasurementsWithData(): List<MeasurementWithData>

    @Transaction
    @Query("SELECT * FROM MeasurementEntity")
    abstract fun getMeasurementsWithDataFlow(): Flow<List<MeasurementWithData>>

    @Transaction
    @Query("SELECT * FROM MeasurementEntity WHERE id = :id")
    abstract suspend fun getMeasurementByIdWithData(id: String): MeasurementWithData
}