package com.example.ndt8.ui.measurement.viewmodels

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.ndt8.CellWatchApp
import com.example.ndt8.data.core.repositories.MeasurementRepository
import com.example.ndt8.data.model.Measurement
import com.example.ndt8.domain.ndt8.managers.Ndt8MeasurementManager
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MeasurementViewModel(private val repository: MeasurementRepository) : ViewModel() {
    private val TAG = this::class.simpleName
    val allMeasurements: LiveData<List<Measurement>> = repository.allMeasurements.asLiveData()

    val allMeasurementsWithData: LiveData<List<Measurement>> =
        repository.allMeasurementsWithData.asLiveData()

    var bytesPerSecState: StateFlow<Double> = Ndt8MeasurementManager.bytesPerSecState

    private var _maxBytesPerSec: MutableLiveData<Double> = MutableLiveData(0.0)
    val maxBytesPerSec: LiveData<Double>
        get() = _maxBytesPerSec

    init {
        viewModelScope.launch {
            bytesPerSecState.collect { bytesPerSec ->
//                updateSpeedometer(8 * bytesPerSec / 1e6)
                Log.d(TAG, "maxBytesPerSec = ${maxBytesPerSec.value.toString()}")
                _maxBytesPerSec.value = _maxBytesPerSec.value?.let { maxOf(it, bytesPerSec) }
            }
        }
    }
    /**
     * Launching a new coroutine to insert the data in a non-blocking way
     */
    fun insertMeasurement(measurement: Measurement) = viewModelScope.launch {
        repository.insertMeasurement(measurement)
    }

    fun getMeasurementsWithData() = viewModelScope.launch {
        repository.getMeasurementsWithData()
    }

    suspend fun runTestSequence() {
        Ndt8MeasurementManager.runTestSequence()
    }
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