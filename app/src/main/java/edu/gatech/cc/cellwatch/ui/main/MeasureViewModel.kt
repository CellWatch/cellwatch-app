package edu.gatech.cc.cellwatch.ui.main

import android.content.ComponentName
import android.content.ServiceConnection
import android.os.IBinder
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.gatech.cc.cellwatch.CellWatchApp
import edu.gatech.cc.cellwatch.core.util.Log
import edu.gatech.cc.cellwatch.data.core.sync.UploadWorker
import edu.gatech.cc.cellwatch.data.model.Measurement
import edu.gatech.cc.cellwatch.data.model.MeasurementGroup
import edu.gatech.cc.cellwatch.domain.fcc.MeasurementService
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant

class MeasureViewModel: ViewModel() {
    private val TAG = this::class.simpleName
    private val _state = MutableStateFlow(State(MeasureProgress.PRE, null, null, false, null))
    val state: StateFlow<State> = _state
    private var uploadTimeJob: Job? = null

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
                            old.moveTo(MeasureProgress.START).copy(
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
        uploadTimeJob?.cancel()
        _state.update { State(MeasureProgress.PRE, null, null, false, null) }
    }

    /** What the step indicator should show right now. */
    fun stepProgress(nowMillis: Long = SystemClock.elapsedRealtime()): MeasureStepProgress {
        val s = _state.value
        return MeasureSteps.compute(
            s.progress,
            s.stoppedDuring,
            MeasureSteps.finishedSteps(s.results),
            s.progressSinceMillis?.let { nowMillis - it } ?: 0L,
        )
    }

    private fun handleLocateStart() {
        _state.update { currentState -> currentState.moveTo(MeasureProgress.LOCATE) }
    }

    private fun handleLatencyStart() {
        _state.update { currentState -> currentState.moveTo(MeasureProgress.LATENCY) }
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
        _state.update { currentState -> currentState.moveTo(MeasureProgress.DOWNLOAD) }
    }

    private fun handleUploadStart() {
        _state.update { currentState -> currentState.moveTo(MeasureProgress.UPLOAD) }
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
            _state.update { currentState ->
                currentState.moveTo(MeasureProgress.ERROR).copy(
                    errorMessage = msg,
                    // DONE can arrive twice (one collector per bind); keep the first answer.
                    stoppedDuring = if (currentState.progress == MeasureProgress.ERROR) {
                        currentState.stoppedDuring
                    } else {
                        currentState.progress
                    },
                )
            }
            return
        }

        _state.update { currentState -> currentState.moveTo(MeasureProgress.END).copy(results = group) }

        UploadWorker.enqueue(CellWatchApp.applicationContext())

        uploadTimeJob?.cancel()
        uploadTimeJob = viewModelScope.launch {
            CellWatchApp.measurementRepository.uploadTimeFlow(group)
                .catch { e -> Log.d(TAG, "failed to read upload time", e) }
                .collect { uploadTime -> _state.update { currentState -> currentState.copy(uploadTime = uploadTime) } }
        }
    }

    data class State(
        val progress: MeasureProgress,
        val results: MeasurementGroup?,
        val uploadTime: Instant?,
        val inVehicle: Boolean,
        val errorMessage: String?,
        /** [SystemClock.elapsedRealtime] when [progress] last changed. */
        val progressSinceMillis: Long? = null,
        /** For [MeasureProgress.ERROR]: the progress the measurement was in when it stopped. */
        val stoppedDuring: MeasureProgress? = null,
    ) {
        /**
         * Moves to [next], noting when that happened. The service's state is collected again every
         * time the activity rebinds, so the same state can arrive more than once; that must not
         * restart the clock for the current step.
         */
        fun moveTo(next: MeasureProgress): State =
            if (next == progress) this
            else copy(progress = next, progressSinceMillis = SystemClock.elapsedRealtime())
    }

    enum class MeasureProgress { PRE, START, LOCATE, LATENCY, DOWNLOAD, UPLOAD, END, ERROR }
}