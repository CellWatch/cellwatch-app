package edu.gatech.cc.cellwatch.ui.measurement.viewmodels

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import edu.gatech.cc.cellwatch.data.model.Measurement
import edu.gatech.cc.cellwatch.domain.fcc.MeasurementManager
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MeasurementViewModel(private val repository: edu.gatech.cc.cellwatch.data.core.repositories.MeasurementRepository) : ViewModel() {
    private val TAG = this::class.simpleName
    val allMeasurements: LiveData<List<Measurement>> = repository.allMeasurements.asLiveData()

    val allMeasurementsWithData: LiveData<List<Measurement>> =
        repository.allMeasurementsWithData.asLiveData()

    val bytesPerSecState: StateFlow<Double> = MeasurementManager.bytesPerSecState

    init {
        viewModelScope.launch {
            bytesPerSecState.collect { bytesPerSec ->
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
//        MeasurementManager.runTestSequence()
    }
}

class MeasurementViewModelFactory(private val repository: edu.gatech.cc.cellwatch.data.core.repositories.MeasurementRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MeasurementViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MeasurementViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
