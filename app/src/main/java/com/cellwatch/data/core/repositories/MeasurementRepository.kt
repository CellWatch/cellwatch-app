package com.cellwatch.data.core.repositories

import android.util.Log
import androidx.annotation.WorkerThread
import com.cellwatch.data.local.model.MeasurementEntity
import com.cellwatch.data.local.model.MeasurementWithData
import com.cellwatch.data.local.dao.MeasurementDao
import com.cellwatch.data.local.model.asExternalModel
import com.cellwatch.data.model.Measurement
import com.cellwatch.data.model.asEntity
import com.cellwatch.data.model.asEntityWithData
import com.cellwatch.data.network.NetworkMeasurementDatasource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class MeasurementRepository(
    private val measurementDao: MeasurementDao,
    private val networkDataSource: NetworkMeasurementDatasource
    ) {
//object MeasurementRepository {
//    private val measurementDao: MeasurementDao = MeasurementDao()

    private val TAG = this::class.simpleName

    val allMeasurements: Flow<List<Measurement>> =
        measurementDao.getMeasurementsFlow().map { it.map(MeasurementEntity::asExternalModel) }

    val allMeasurementsWithData: Flow<List<Measurement>> =
        measurementDao.getMeasurementsWithDataFlow().map { it.map(MeasurementWithData::asExternalModel) }

    @WorkerThread
    suspend fun getMeasurementByIdWithData(id: String): Measurement =
        measurementDao.getMeasurementByIdWithData(id).asExternalModel()

    @WorkerThread
    suspend fun getMeasurementById(id: String): Measurement =
        measurementDao.getMeasurementById(id).asExternalModel()

    @WorkerThread
    suspend fun getMeasurements(): List<Measurement> =
        measurementDao.getMeasurements().map(MeasurementEntity::asExternalModel)

    @WorkerThread
    suspend fun getUnsynchronizedMeasurements(): List<Measurement> =
        measurementDao.getUnsynchronizedMeasurements().map(MeasurementEntity::asExternalModel)

    @WorkerThread
    suspend fun getMeasurementsWithData(): List<Measurement> =
        measurementDao.getMeasurementsWithData().map(MeasurementWithData::asExternalModel)

    @WorkerThread
    suspend fun getUnsynchronizedMeasurementsWithData(): List<Measurement> =
        measurementDao.getUnsynchronizedMeasurementsWithData().map(MeasurementWithData::asExternalModel)

//    @Suppress("RedundantSuspendModifier")
    @WorkerThread
    suspend fun insertMeasurement(measurement: Measurement) {
        val measurementWithDataEntity = measurement.asEntityWithData()
        Log.d(TAG, "MeasurementRepository.insertMeasurement: measurementWithDataEntity.cells length is ${measurementWithDataEntity.cells?.size}")
        Log.d(TAG, "MeasurementRepository.insertMeasurement: measurementWithDataEntity.simMcc is ${measurementWithDataEntity.measurement?.simMcc}")
        measurementDao.insertMeasurementWithData(measurementWithDataEntity)
    }

    @WorkerThread
    suspend fun updateMeasurement(measurement: Measurement) {
        measurementDao.updateMeasurement(measurement.asEntity())
    }

//    @Suppress("RedundantSuspendModifier")
//    @WorkerThread
//    suspend fun insertMeasurementWithData(measurementWithData: MeasurementWithData) {
//        measurementDao.insertMeasurementWithData(measurementWithData)
//    }

    @WorkerThread
    suspend fun deleteAllMeasurements() = measurementDao.deleteAllMeasurements()

    /**
     * Store-and-forward measurement data
     * Query for any measurement data in local Room database and upload it to
     * the network database (Supabase). Clear local database if successful.
     */
    @WorkerThread
    suspend fun uploadMeasurements() {
        val measurements = getUnsynchronizedMeasurementsWithData()

        if (measurements.isNotEmpty()) {
            Log.d(TAG, "uploadMeasurements: Attempting to upload ${measurements.size} measurements")
            Log.d(TAG, "uploadMeasurements: measurements[0].simMnc = ${measurements[0].simMnc}")
            try {
                networkDataSource.insertMeasurements(measurements)
            } catch (e: Exception) {
                Log.e(TAG, "Error in uploadMeasurements: ${e.message}")
                throw e
            }

            Log.d(TAG, "Update ${measurements.size} measurements as synchronized")

            measurements.forEach { measurement ->
                val measurementEntity = measurement.asEntity()
                measurementEntity.isSynchronized = true
                measurementDao.updateMeasurement(measurementEntity)
            }

//            measurements.forEach { measurement ->
//                try {
//                    networkDataSource.insertMeasurement(measurement)
////                    deleteAllMeasurements()
//                } catch (e: Exception) {
//                    Log.e(TAG, "Error in uploadMeasurementsWithData: ${e.message}")
//                    throw e
//                }
//
//            }
        } else {
            Log.d(TAG, "No measurements to upload !!!")
        }
    }
}
