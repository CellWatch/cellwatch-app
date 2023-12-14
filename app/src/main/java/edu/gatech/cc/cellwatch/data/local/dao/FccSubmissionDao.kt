package edu.gatech.cc.cellwatch.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import edu.gatech.cc.cellwatch.data.local.model.CellEntity
import edu.gatech.cc.cellwatch.data.local.model.FccSubmissionEntity
import edu.gatech.cc.cellwatch.data.local.model.FccSubmissionWithMeasurements
import edu.gatech.cc.cellwatch.data.local.model.MeasurementEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class FccSubmissionDao {
    @Insert
    abstract suspend fun insertFccSubmission(fccSubmission: FccSubmissionEntity)

//    @Transaction
//    open suspend fun insertFccSubmissionWithMeasurements(fccSubmissionWithMeasurements: FccSubmissionWithMeasurements) {
//        insertFccSubmission(fccSubmissionWithMeasurements.fccSubmission)
//
//        with(fccSubmissionWithMeasurements) {
//            if (measurementsWithData != null) {
//                measurementsWithData.map {
//                        measurement -> measurement.measurement.groupId = fccSubmission.groupId
//                }
//                MeasurementDao.
//            }
//        }
//    }

    @Update
    abstract suspend fun updateFccSubmission(fccSubmission: FccSubmissionEntity)

    @Delete
    abstract suspend fun deleteFccSubmission(fccSubmission: FccSubmissionEntity)

    @Query("DELETE FROM FccSubmissionEntity")
    abstract suspend fun deleteAllFccSubmissions()

    @Query("SELECT * FROM FccSubmissionEntity WHERE id = :id")
    abstract suspend fun getFccSubmissionById(id: String): FccSubmissionEntity

    @Transaction
    @Query("SELECT * FROM FccSubmissionEntity ORDER BY id")
    abstract suspend fun getFccSubmissionsWithMeasurements(): List<FccSubmissionWithMeasurements>

    @Query("SELECT * FROM FccSubmissionEntity")
    abstract suspend fun getFccSubmissions(): List<FccSubmissionEntity>

    @Transaction
    @Query("SELECT * FROM FccSubmissionEntity WHERE isSynchronized = 0 ORDER BY id")
    abstract suspend fun getUnsynchronizedFccSubmissionsWithMeasurements(): List<FccSubmissionWithMeasurements>

    @Query("SELECT * FROM FccSubmissionEntity WHERE isSynchronized = 0")
    abstract suspend fun getUnsynchronizedFccSubmissions(): List<FccSubmissionEntity>

    @Query("SELECT * FROM FccSubmissionEntity")
    abstract fun getFccSubmissionsFlow(): Flow<List<FccSubmissionEntity>>

    @Query("SELECT * FROM FccSubmissionEntity" +
                " JOIN MeasurementEntity ON FccSubmissionEntity.id = MeasurementEntity.groupId")
    abstract suspend fun getFccSubmissionsAndMeasurements(): Map<FccSubmissionEntity, List<MeasurementEntity>>

//    @Query("SELECT * FROM FccSubmissionEntity" +
//            " JOIN MeasurementEntity ON FccSubmissionEntity.groupId = MeasurementEntity.groupId" +
//            " JOIN CellEntity ON CellEntity.measurementId = MeasurementEntity.id")
//    abstract suspend fun getFccSubmissionsAndMeasurementsWithData(): Map<FccSubmissionEntity, List<Map<MeasurementEntity, List<CellEntity>>>>

//    @Transaction
//    @Query("SELECT * FROM FccSubmissionEntity")
//    abstract suspend fun getFccSubmissionsWithMeasurements(): List<FccSubmissionWithMeasurements>
//
//    @Transaction
//    @Query("SELECT * FROM FccSubmissionEntity WHERE isSynchronized = 0")
//    abstract suspend fun getUnsynchronizedFccSubmissionsWithMeasurements(): List<FccSubmissionWithMeasurements>
//
//    @Transaction
//    @Query("SELECT * FROM FccSubmissionEntity")
//    abstract fun getFccSubmissionsWithMeasurementsFlow(): Flow<List<FccSubmissionWithMeasurements>>
//
//    @Transaction
//    @Query("SELECT * FROM FccSubmissionEntity WHERE id = :id")
//    abstract suspend fun getFccSubmissionByIdWithMeasurements(id: String): FccSubmissionWithMeasurements
}
