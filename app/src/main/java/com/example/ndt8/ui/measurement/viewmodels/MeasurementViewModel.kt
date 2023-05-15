package com.example.ndt8.ui.measurement.viewmodels

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.ndt8.data.core.repositories.MeasurementRepository
import com.example.ndt8.data.model.Measurement
import kotlinx.coroutines.launch

class MeasurementViewModel(private val repository: MeasurementRepository) : ViewModel() {
    val allMeasurements: LiveData<List<Measurement>> = repository.allMeasurements.asLiveData()

    val allMeasurementsWithData: LiveData<List<Measurement>> =
        repository.allMeasurementsWithData.asLiveData()

    /**
     * Launching a new coroutine to insert the data in a non-blocking way
     */
    fun insertMeasurement(measurement: Measurement) = viewModelScope.launch {
        repository.insertMeasurement(measurement)
    }

//    fun insertMeasurementWithData(measurementWithData: MeasurementWithData) = viewModelScope.launch {
//        repository.insertMeasurementWithData(measurementWithData)
//    }

    fun getMeasurementsWithData() = viewModelScope.launch {
        repository.getMeasurementsWithData()
    }

//    fun insertMeasurementWithLocationsAndData(
//        measurement: Measurement,
//        locations: List<Location>,
//        uploadDownloadData: UploadDownloadData?,
//        latencyData: LatencyData?
//    ) = viewModelScope.launch {
//        repository.insertMeasurementWithLocationsAndData(
//            measurement,
//            locations,
//            uploadDownloadData,
//            latencyData
//        )
//    }
}

class MeasurementViewModelFactory(private val repository: MeasurementRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MeasurementViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MeasurementViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}