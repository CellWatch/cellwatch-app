package com.example.ndt8.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.example.ndt8.data.local.model.UploadDownloadDataEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UploadDownloadDataDao {
    @Insert
    suspend fun insertUploadDownloadData(uploadDownloadDataEntity: UploadDownloadDataEntity)

    @Delete
    suspend fun deleteUploadDownloadData(uploadDownloadDataEntity: UploadDownloadDataEntity)

    @Query("SELECT * FROM UploadDownloadDataEntity")
    suspend fun getUploadDownloadData(): List<UploadDownloadDataEntity>

    @Query("SELECT * FROM UploadDownloadDataEntity")
    fun getUploadDownloadDataFlow(): Flow<List<UploadDownloadDataEntity>>

    @Query("SELECT * FROM UploadDownloadDataEntity WHERE measurementId = :measurementId")
    suspend fun getUploadDownloadDataByMeasurementId(measurementId: String): List<UploadDownloadDataEntity>

    @Query("SELECT * FROM UploadDownloadDataEntity WHERE measurementId = :measurementId")
    fun getUploadDownloadDataByMeasurementIdFlow(measurementId: String): Flow<List<UploadDownloadDataEntity>>
}