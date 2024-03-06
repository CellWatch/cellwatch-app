package edu.gatech.cc.cellwatch.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import edu.gatech.cc.cellwatch.data.local.model.CellEntity
import edu.gatech.cc.cellwatch.data.local.model.LatencyDataEntity
import edu.gatech.cc.cellwatch.data.local.model.LocationEntity
import edu.gatech.cc.cellwatch.data.local.model.MeasurementEntity
import edu.gatech.cc.cellwatch.data.local.model.MeasurementWithData
import edu.gatech.cc.cellwatch.data.local.model.UploadDownloadDataEntity
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
            cells?.forEach { cell ->
                cell.measurementId = measurement.id
                insertCell(cell)
            }
            locations?.forEach { location ->
                location.measurementId = measurement.id
                insertLocation(location)
            }
        }
    }

    @Insert
    abstract suspend fun insertUploadDownloadData(uploadDownloadDataEntity: UploadDownloadDataEntity)

    @Insert
    abstract suspend fun insertLatencyData(latencyDataEntity: LatencyDataEntity)

    @Insert
    abstract suspend fun insertCell(cellEntity: CellEntity)

    @Insert
    abstract suspend fun insertLocation(locationEntity: LocationEntity)

    @Update
    abstract suspend fun updateMeasurement(measurement: MeasurementEntity)

    @Delete
    abstract suspend fun deleteMeasurement(measurement: MeasurementEntity)

    @Query("DELETE FROM MeasurementEntity")
    abstract suspend fun deleteAllMeasurements()

    @Query("SELECT * FROM MeasurementEntity WHERE id = :id")
    abstract suspend fun getMeasurementById(id: String): MeasurementEntity

    @Query("SELECT * FROM MeasurementEntity")
    abstract suspend fun getMeasurements(): List<MeasurementEntity>

    @Query("SELECT * FROM MeasurementEntity WHERE uploadTime IS NULL")
    abstract suspend fun getUnsynchronizedMeasurements(): List<MeasurementEntity>

    @Query("SELECT * FROM MeasurementEntity")
    abstract fun getMeasurementsFlow(): Flow<List<MeasurementEntity>>

    @Transaction
    @Query("SELECT * FROM MeasurementEntity")
    abstract suspend fun getMeasurementsWithData(): List<MeasurementWithData>

    @Transaction
    @Query("SELECT * FROM MeasurementEntity WHERE uploadTime IS NULL")
    abstract suspend fun getUnsynchronizedMeasurementsWithData(): List<MeasurementWithData>

    @Transaction
    @Query("SELECT * FROM MeasurementEntity")
    abstract fun getMeasurementsWithDataFlow(): Flow<List<MeasurementWithData>>

    @Transaction
    @Query("SELECT * FROM MeasurementEntity WHERE id = :id")
    abstract suspend fun getMeasurementByIdWithData(id: String): MeasurementWithData
}
