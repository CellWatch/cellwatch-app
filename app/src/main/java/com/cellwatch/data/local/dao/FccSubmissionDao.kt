package com.cellwatch.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.cellwatch.data.local.model.FccSubmissionEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class FccSubmissionDao {
    @Insert
    abstract suspend fun insertFccSubmission(measurement: FccSubmissionEntity)

    @Update
    abstract suspend fun updateFccSubmission(measurement: FccSubmissionEntity)

    @Delete
    abstract suspend fun deleteFccSubmission(measurement: FccSubmissionEntity)

    @Query("DELETE FROM FccSubmissionEntity")
    abstract suspend fun deleteAllFccSubmissions()

    @Query("SELECT * FROM FccSubmissionEntity WHERE id = :id")
    abstract suspend fun getFccSubmissionById(id: String): FccSubmissionEntity

    @Query("SELECT * FROM FccSubmissionEntity")
    abstract suspend fun getFccSubmissions(): List<FccSubmissionEntity>

    @Query("SELECT * FROM FccSubmissionEntity WHERE isSynchronized = 0")
    abstract suspend fun getUnsynchronizedFccSubmissions(): List<FccSubmissionEntity>

    @Query("SELECT * FROM FccSubmissionEntity")
    abstract fun getFccSubmissionsFlow(): Flow<List<FccSubmissionEntity>>

}