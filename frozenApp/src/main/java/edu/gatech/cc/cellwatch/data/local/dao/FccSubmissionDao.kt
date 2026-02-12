package edu.gatech.cc.cellwatch.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import edu.gatech.cc.cellwatch.data.local.model.FccSubmissionEntity
import edu.gatech.cc.cellwatch.data.local.model.FccSubmissionWithMeasurements
import kotlinx.coroutines.flow.Flow

@Dao
abstract class FccSubmissionDao {
    @Insert
    abstract suspend fun insertFccSubmission(fccSubmission: FccSubmissionEntity)

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

    @Query("SELECT * FROM FccSubmissionEntity WHERE uploadTime IS NULL")
    abstract suspend fun getUnsynchronizedFccSubmissions(): List<FccSubmissionEntity>

    @Query("SELECT * FROM FccSubmissionEntity")
    abstract fun getFccSubmissionsFlow(): Flow<List<FccSubmissionEntity>>
}
