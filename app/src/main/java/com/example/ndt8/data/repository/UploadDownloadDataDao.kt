package com.example.ndt8.data.repository

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import com.example.ndt8.data.entities.UploadDownloadData

@Dao
interface UploadDownloadDataDao {
    @Insert
    fun insertUploadDownloadData(measurement: UploadDownloadData)

    @Delete
    fun deleteUploadDownloadData(measurement: UploadDownloadData)

    @Query("SELECT * FROM UploadDownloadData")
    fun getUploadDownloadData(): List<UploadDownloadData>
}