package edu.gatech.cc.cellwatch.data.core.repositories

import edu.gatech.cc.cellwatch.core.util.Log
import androidx.annotation.WorkerThread
import edu.gatech.cc.cellwatch.data.local.dao.FccSubmissionDao
import edu.gatech.cc.cellwatch.data.local.model.MeasurementWithData
import edu.gatech.cc.cellwatch.data.local.dao.MeasurementDao
import edu.gatech.cc.cellwatch.data.local.model.FccSubmissionEntity
import edu.gatech.cc.cellwatch.data.local.model.asExternalModel
import edu.gatech.cc.cellwatch.data.model.FccSubmission
import edu.gatech.cc.cellwatch.data.model.Measurement
import edu.gatech.cc.cellwatch.data.model.MeasurementGroup
import edu.gatech.cc.cellwatch.data.model.asEntity
import edu.gatech.cc.cellwatch.data.model.asEntityWithData
import edu.gatech.cc.cellwatch.data.network.NetworkMeasurementDatasource
import edu.gatech.cc.cellwatch.data.network.NetworkRestResult
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant


/**
 * A MeasurementRepository contains methods to read/write [Measurement] records to the local Room
 * database. MeasurementRepository also contains the uploadMeasurements method which uploads all
 * unsynchronized [Measurement] records from the Room database to the remote Supabase database.
 */

class MeasurementRepository(
    private val measurementDao: MeasurementDao,
    private val submissionDao: FccSubmissionDao,
    private val networkDataSource: NetworkMeasurementDatasource
) {
    private val TAG = this::class.simpleName

    @WorkerThread
    suspend fun getMeasurementGroups(): List<MeasurementGroup> {
        val measurements = getMeasurementsWithData()
        val submissionList = submissionDao.getFccSubmissions().map(FccSubmissionEntity::asExternalModel)
        val latency = mutableMapOf<String, Measurement>()
        val download = mutableMapOf<String, Measurement>()
        val upload = mutableMapOf<String, Measurement>()

        measurements.forEach {
            if (it.groupId == null) {
                throw RuntimeException("measurement ${it.id} missing group id")
            }

            when (it.type) {
                "latency" -> latency[it.groupId] = it
                "download" -> download[it.groupId] = it
                "upload" -> upload[it.groupId] = it
                else -> throw RuntimeException("unknown measurement type ${it.type}")
            }
        }

        val submissions = mutableMapOf<String, FccSubmission>()
        submissionList.forEach { submissions[it.id] = it }

        val groups = mutableMapOf<String, MeasurementGroup>()
        measurements.forEach {
            if (it.groupId == null) {
                throw RuntimeException("measurement ${it.id} missing group id")
            }

            if (groups[it.groupId] == null) {
                groups[it.groupId] = MeasurementGroup(
                    latency[it.groupId],
                    download[it.groupId],
                    upload[it.groupId],
                    submissions[it.groupId],
                )
            }
        }

        return groups.values.toList()
    }

    @WorkerThread
    suspend fun getMeasurementsWithData(): List<Measurement> =
        measurementDao.getMeasurementsWithData().map(MeasurementWithData::asExternalModel)

    @WorkerThread
    suspend fun getUnsynchronizedMeasurementsWithData(): List<Measurement> =
        measurementDao.getUnsynchronizedMeasurementsWithData().map(MeasurementWithData::asExternalModel)

    @WorkerThread
    suspend fun insertMeasurement(measurement: Measurement) {
        val measurementWithDataEntity = measurement.asEntityWithData()
//        Log.d(TAG, "MeasurementRepository.insertMeasurement: measurementWithDataEntity.cells length is ${measurementWithDataEntity.cells?.size}")
//        Log.d(TAG, "MeasurementRepository.insertMeasurement: measurementWithDataEntity.simMcc is ${measurementWithDataEntity.measurement.simMcc}")
        measurementDao.insertMeasurementWithData(measurementWithDataEntity)
    }

    @WorkerThread
    suspend fun updateMeasurement(measurement: Measurement) {
        val measurementEntity = measurement.asEntity()
        measurementDao.updateMeasurement(measurementEntity)
    }

    /**
     * Store-and-forward measurement data
     * Query for any measurement data in local Room database and upload it to
     * the network database (Supabase). Clear local database if successful.
     */
    @WorkerThread
    suspend fun uploadMeasurements(): Instant? {
        val results: List<NetworkRestResult>
        val unsynchronizedMeasurements = getUnsynchronizedMeasurementsWithData()

        if (unsynchronizedMeasurements.isNotEmpty()) {
            Log.d(TAG, "uploadMeasurements: Attempting to upload ${unsynchronizedMeasurements.size} measurements")
            try {
                results = networkDataSource.uploadMeasurements(unsynchronizedMeasurements)
//                networkDataSource.insertMeasurements(measurements)
                val errors = results.filter { it.error != null }.map { it.error }
                Log.d(TAG, "************** Number of errors: ${errors.size}")
                errors.map {
                    val isDuplicateKeyError = it?.error?.startsWith("duplicate key value violates unique constraint") ?: false
                    if (isDuplicateKeyError) {
                        Log.d(TAG, "!!!!!!!! Duplicate key error !!!!!!!!")
                    } else {
                        Log.d(TAG, "!!!!!!! Unknown REST error !!!!!!!")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in uploadMeasurements: ${e.message}")
                throw e
            }

            // Get all duplicate key value errors to check for already synced measurements.
            val errorResults = results.filter {
                it.error != null && it.error.error.startsWith("duplicate key value violates unique constraint")
            }.map { it }

            // If there exists a synced measurement with the same timestamp,
            // assume duplicate key means this measurement was already successfully synced with Supabase
            // so, update measurement.uploadTime.
            errorResults.forEach { errorResult ->
                try {
                    val networkMeasurement = networkDataSource.getMeasurementById(errorResult.measurement.id)
                    if (networkMeasurement != null) {
                        if (networkMeasurement.timestamp == errorResult.measurement.timestamp)
                            errorResult.measurement.uploadTime = Clock.System.now()
                            updateMeasurement(errorResult.measurement)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in uploadMeasurements: ${e.message}")
                    throw e
                }
            }

            return Clock.System.now()
        } else {
            Log.d(TAG, "No measurements to upload !!!")
            return null
        }
    }

//    @WorkerThread
//    suspend fun uploadMeasurementsTest(): Instant? {
//        val measurements = getUnsynchronizedMeasurementsWithData()
//
//        if (measurements.isNotEmpty()) {
//            Log.d(TAG, "uploadMeasurements: Attempting to upload ${measurements.size} measurements")
//            try {
//                networkDataSource.insertMeasurementsTest(measurements)
//            } catch (e: Exception) {
//                Log.e(TAG, "Error in uploadMeasurements: ${e.message}")
//                throw e
//            }
//
//            Log.d(TAG, "Update ${measurements.size} measurements as synchronized")
//            val uploadTime = Clock.System.now()
//
//            measurements.forEach { measurement ->
//                val measurementEntity = measurement.asEntity()
//                measurementEntity.uploadTime = uploadTime
//                measurementDao.updateMeasurement(measurementEntity)
//            }
//
//            return uploadTime
//        } else {
//            Log.d(TAG, "No measurements to upload !!!")
//            return null
//        }
//    }
}
