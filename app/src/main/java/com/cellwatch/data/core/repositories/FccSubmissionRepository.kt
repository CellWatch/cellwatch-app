package com.cellwatch.data.core.repositories

import android.util.Log
import androidx.annotation.WorkerThread
import com.cellwatch.data.local.model.FccSubmissionEntity
import com.cellwatch.data.local.model.FccSubmissionWithMeasurements
import com.cellwatch.data.local.dao.FccSubmissionDao
import com.cellwatch.data.local.model.asExternalModel
import com.cellwatch.data.model.FccSubmission
import com.cellwatch.data.model.asEntity
import com.cellwatch.data.network.NetworkMeasurementDatasource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class FccSubmissionRepository(
    private val fccSubmissionDao: FccSubmissionDao,
    private val networkDataSource: NetworkMeasurementDatasource
) {
    private val TAG = this::class.simpleName

    val allFccSubmissions: Flow<List<FccSubmission>> =
        fccSubmissionDao.getFccSubmissionsFlow().map { it.map(FccSubmissionEntity::asExternalModel) }

//    val allFccSubmissionsWithMeasurements: Flow<List<FccSubmission>> =
//        FccSubmissionDao.getFccSubmissionsWithMeasurementsFlow().map { it.map(FccSubmissionWithMeasurements::asExternalModel) }

//    @WorkerThread
//    suspend fun getFccSubmissionByIdWithMeasurements(id: String): FccSubmission =
//        FccSubmissionDao.getFccSubmissionByIdWithMeasurements(id).asExternalModel()

    @WorkerThread
    suspend fun getFccSubmissionById(id: String): FccSubmission =
        fccSubmissionDao.getFccSubmissionById(id).asExternalModel()

    @WorkerThread
    suspend fun getFccSubmissions(): List<FccSubmission> =
        fccSubmissionDao.getFccSubmissions().map(FccSubmissionEntity::asExternalModel)

    @WorkerThread
    suspend fun getFccSubmissionsWithMeasurements(): List<FccSubmission> =
        fccSubmissionDao.getFccSubmissionsWithMeasurements().map(FccSubmissionWithMeasurements::asExternalModel)

    @WorkerThread
    suspend fun getUnsynchronizedFccSubmissions(): List<FccSubmission> =
        fccSubmissionDao.getUnsynchronizedFccSubmissions().map(FccSubmissionEntity::asExternalModel)

    @WorkerThread
    suspend fun getUnsynchronizedFccSubmissionsWithMeasurements(): List<FccSubmission> =
        fccSubmissionDao.getUnsynchronizedFccSubmissionsWithMeasurements().map(FccSubmissionWithMeasurements::asExternalModel)

    //    @Suppress("RedundantSuspendModifier")
    @WorkerThread
    suspend fun insertFccSubmission(FccSubmission: FccSubmission) {
        val FccSubmissionEntity = FccSubmission.asEntity()
        fccSubmissionDao.insertFccSubmission(FccSubmissionEntity)
    }

    @WorkerThread
    suspend fun updateFccSubmission(FccSubmission: FccSubmission) {
        fccSubmissionDao.updateFccSubmission(FccSubmission.asEntity())
    }

//    @Suppress("RedundantSuspendModifier")
//    @WorkerThread
//    suspend fun insertFccSubmissionWithMeasurements(FccSubmissionWithMeasurements: FccSubmissionWithMeasurements) {
//        FccSubmissionDao.insertFccSubmissionWithMeasurements(FccSubmissionWithMeasurements)
//    }

    @WorkerThread
    suspend fun deleteAllFccSubmissions() = fccSubmissionDao.deleteAllFccSubmissions()

    /**
     * Store-and-forward FccSubmission data
     * Query for any FccSubmission data in local Room database and upload it to
     * the network database (Supabase). Clear local database if successful.
     */
    @WorkerThread
    suspend fun uploadFccSubmissions() {
        val fccSubmissions = getUnsynchronizedFccSubmissions()
//        val fccSubmissions = getUnsynchronizedFccSubmissionsWithMeasurements()

        if (fccSubmissions.isNotEmpty()) {
            Log.d(TAG, "uploadFccSubmissions: Attempting to upload ${fccSubmissions.size} FccSubmissions")
            try {
                networkDataSource.insertFccSubmissions(fccSubmissions)
            } catch (e: Exception) {
                Log.e(TAG, "Error in uploadFccSubmissions: ${e.message}")
                throw e
            }

            Log.d(TAG, "Update ${fccSubmissions.size} FccSubmissions as synchronized")

            fccSubmissions.forEach { FccSubmission ->
                val FccSubmissionEntity = FccSubmission.asEntity()
                FccSubmissionEntity.isSynchronized = true
                fccSubmissionDao.updateFccSubmission(FccSubmissionEntity)
            }
        } else {
            Log.d(TAG, "No FccSubmissions to upload !!!")
        }
    }
}
