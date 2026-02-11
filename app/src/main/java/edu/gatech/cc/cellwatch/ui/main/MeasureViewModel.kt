package edu.gatech.cc.cellwatch.ui.main

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.gatech.cc.cellwatch.CellWatchApp
import edu.gatech.cc.cellwatch.core.util.Log
import edu.gatech.cc.cellwatch.data.model.Measurement
import edu.gatech.cc.cellwatch.data.model.MeasurementGroup
import edu.gatech.cc.cellwatch.domain.fcc.MeasurementService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant

class MeasureViewModel: ViewModel() {
    private val TAG = this::class.simpleName
    private val _state = MutableStateFlow(State(MeasureProgress.PRE, null, null, false, null))
    val state: StateFlow<State> = _state

    val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            if (service !is MeasurementService.MeasurementBinder) {
                Log.e(TAG, "expected MeasurementBinder, got $service")
                return
            }

            viewModelScope.launch {
                service.state.collect { serviceState ->
                    when (serviceState) {
                        null, MeasurementService.State.STARTING -> { /* do nothing */ }
                        MeasurementService.State.STARTED -> _state.update { old ->
                            old.copy(
                                progress = MeasureProgress.START,
                                results = MeasurementGroup(
                                    null,
                                    null,
                                    null,
                                    null,
                                    service.groupId ?: throw RuntimeException("missing service group id")
                                ),
                                errorMessage = null
                            )
                        }

                        MeasurementService.State.LOCATE -> handleLocateStart()
                        MeasurementService.State.LATENCY -> handleLatencyStart()
                        MeasurementService.State.DOWNLOAD -> {
                            service.latency?.let { handleLatencyComplete(it) }
                            handleDownloadStart()
                        }

                        MeasurementService.State.UPLOAD -> {
                            service.download?.let { handleThroughputComplete(it) }
                            handleUploadStart()
                        }

                        MeasurementService.State.DONE -> {
                            service.upload?.let { handleThroughputComplete(it) }
                            handleMeasurementComplete(
                                service.group,
                                service.lastErrorCode,
                                service.lastErrorMessage
                            )
                        }
                    }
                }
            }
        }

        override fun onServiceDisconnected(className: ComponentName) {
            Log.w(TAG, "service disconnected $className")
        }
    }

    fun setInVehicle(inVehicle: Boolean) {
        _state.update { currentState -> currentState.copy(inVehicle = inVehicle) }
    }

    fun reset() {
        _state.update { State(MeasureProgress.PRE, null, null, false, null) }
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

    private fun handleMeasurementComplete(group: MeasurementGroup?, errorCode: Int?, errorText: String?) {
        if (group == null) {
            val msg = when (errorCode) {
                429 -> "You’re temporarily rate-limited by the test servers. Please wait a few minutes and try again."
                else -> errorText ?: "Measurement failed. Please try again."
            }
            _state.update { currentState -> currentState.copy(progress = MeasureProgress.ERROR, errorMessage = msg) }
            return
        }

        _state.update { currentState -> currentState.copy(progress = MeasureProgress.END, results = group) }

        viewModelScope.launch {
            val uploadTime = try {
                CellWatchApp.sharedMeasurementSyncService.syncAll()
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
        val errorMessage: String?
    )

    enum class MeasureProgress { PRE, START, LOCATE, LATENCY, DOWNLOAD, UPLOAD, END, ERROR }
}
