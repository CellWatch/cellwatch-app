package edu.gatech.cc.cellwatch.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.gatech.cc.cellwatch.CellWatchApp
import edu.gatech.cc.cellwatch.core.util.Log
import edu.gatech.cc.cellwatch.data.model.Measurement
import edu.gatech.cc.cellwatch.data.model.MeasurementGroup
import edu.gatech.cc.cellwatch.domain.fcc.MeasurementManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant

class MeasureViewModel: ViewModel() {
    private val TAG = this::class.simpleName
    private val _state = MutableStateFlow(State(MeasureProgress.PRE, null, null, false))
    val state: StateFlow<State> = _state

    fun setInVehicle(inVehicle: Boolean) {
        _state.update { currentState -> currentState.copy(inVehicle = inVehicle) }
    }

    fun startMeasurement() {
        viewModelScope.launch {
            val prevState = _state.getAndUpdate { currentState ->
                currentState.copy(
                    progress = MeasureProgress.START,
                    results = MeasurementGroup(null, null, null, null),
                )
            }

            try {
                val group = MeasurementManager.runTestSequence(
                    prevState.inVehicle,
                    { handleLocateStart() },
                    { },
                    { handleLatencyStart() },
                    { handleLatencyComplete(it) },
                    { handleDownloadStart() },
                    { handleThroughputComplete(it) },
                    { handleUploadStart() },
                    { handleThroughputComplete(it) },
                    failIfNotOnCellular = prevState.progress !== MeasureProgress.NOT_CELLULAR,
                )
                handleMeasurementComplete(group)
            } catch (e: MeasurementManager.NotOnCellularException) {
                handleNotOnCellular()
            } catch (e: Exception) {
                Log.e(TAG, "unexpected error running test sequence", e)
                handleMeasurementComplete(null)
            }
        }
    }

    fun cancel() {
        _state.update { currentState -> currentState.copy(progress = MeasureProgress.PRE, results = null, uploadTime = null) }
    }

    fun reset() {
        _state.update { State(MeasureProgress.PRE, null, null, false) }
    }

    private fun handleNotOnCellular() {
        _state.update { currentState -> currentState.copy(progress = MeasureProgress.NOT_CELLULAR) }
    }

    private fun handleLocateStart() {
        _state.update { currentState -> currentState.copy(progress = MeasureProgress.LOCATE) }
    }

    private fun handleLatencyStart() {
        _state.update { currentState -> currentState.copy(progress = MeasureProgress.LATENCY) }
    }

    private fun handleLatencyComplete(m: Measurement) {
        _state.update { currentState ->
            currentState.copy(results = MeasurementGroup(
                    m,
                    currentState.results?.download,
                    currentState.results?.upload,
                    currentState.results?.submission,
                )
            )
        }
    }

    private fun handleDownloadStart() {
        _state.update { currentState -> currentState.copy(progress = MeasureProgress.DOWNLOAD) }
    }

    private fun handleUploadStart() {
        _state.update { currentState -> currentState.copy(progress = MeasureProgress.UPLOAD) }
    }

    private fun handleThroughputComplete(m: Measurement) {
        _state.update { currentState ->
            val group = when (m.type) {
                "download" -> MeasurementGroup(currentState.results?.latency, m, currentState.results?.upload, currentState.results?.submission)
                "upload" -> MeasurementGroup(currentState.results?.latency, currentState.results?.download, m, currentState.results?.submission)
                else -> throw RuntimeException("throughput complete called with non-throughput measurement: ${m.type}")
            }
            currentState.copy(results = group)
        }
    }

    private fun handleMeasurementComplete(group: MeasurementGroup?) {
        if (group == null) {
            _state.update { currentState -> currentState.copy(progress = MeasureProgress.ERROR) }
            return
        }

        _state.update { currentState -> currentState.copy(progress = MeasureProgress.END, results = group) }

        viewModelScope.launch {
            val uploadTime = try {
                CellWatchApp.measurementRepository.tryUploadMeasurements()
                CellWatchApp.measurementRepository.tryUploadFccSubmissions()
                CellWatchApp.measurementRepository.getUploadTime(group)
            } catch (e: Exception) {
                Log.d(TAG, "failed to upload measurements and submission", e)
                null
            }

            _state.update { currentState -> currentState.copy(uploadTime = uploadTime) }
        }
    }

    data class State(
        val progress: MeasureProgress,
        val results: MeasurementGroup?,
        val uploadTime: Instant?,
        val inVehicle: Boolean,
    )

    enum class MeasureProgress { PRE, START, NOT_CELLULAR, LOCATE, LATENCY, DOWNLOAD, UPLOAD, END, ERROR }
}