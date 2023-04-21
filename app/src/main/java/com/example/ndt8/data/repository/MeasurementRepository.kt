package com.example.ndt8.data.repository

import androidx.annotation.WorkerThread
import com.example.ndt8.data.entities.LatencyData
import com.example.ndt8.data.entities.Location
import com.example.ndt8.data.entities.Measurement
import com.example.ndt8.data.entities.UploadDownloadData
import kotlinx.coroutines.flow.Flow

class MeasurementRepository(private val measurementDao: MeasurementDao) {
    val allMeasurements: Flow<List<Measurement>> = measurementDao.getMeasurements()

    @Suppress("RedundantSuspendModifier")
    @WorkerThread
    suspend fun insertMeasurement(measurement: Measurement) {
        measurementDao.insertMeasurement(measurement)
    }

    @Suppress("RedundantSuspendModifier")
    @WorkerThread
    suspend fun insertMeasurementWithLocationsAndData(
        measurement: Measurement,
        locations: List<Location>,
        uploadDownloadData: List<UploadDownloadData>,
        latencyData: List<LatencyData>
    ) {
        measurementDao.insertMeasurementWithLocationsAndData(
            measurement,
            locations,
            uploadDownloadData,
            latencyData
        )
    }
}
