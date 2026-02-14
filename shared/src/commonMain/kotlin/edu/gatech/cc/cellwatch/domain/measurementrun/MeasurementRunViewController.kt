package edu.gatech.cc.cellwatch.domain.measurementrun

import edu.gatech.cc.cellwatch.domain.model.Measurement
import edu.gatech.cc.cellwatch.domain.model.MeasurementGroup
import kotlinx.datetime.Instant

enum class MeasurementRunProgress {
    PRE,
    START,
    LOCATE,
    LATENCY,
    DOWNLOAD,
    UPLOAD,
    END,
    ERROR,
}

enum class MeasurementRunHeaderKey {
    MEASURING,
    FINDING_SERVER,
    MEASURING_LATENCY,
    MEASURING_DOWNLOAD,
    MEASURING_UPLOAD,
    MEASUREMENT_COMPLETE,
    MEASUREMENT_FAILED,
}

data class MeasurementRunState(
    val progress: MeasurementRunProgress = MeasurementRunProgress.PRE,
    val results: MeasurementGroup? = null,
    val uploadTime: Instant? = null,
    val inVehicle: Boolean = false,
    val errorMessage: String? = null,
)

data class MeasurementRunUiModel(
    val headerKey: MeasurementRunHeaderKey,
    val headerText: String,
    val showProgressBar: Boolean,
    val showCompletionActions: Boolean,
)

class MeasurementRunUiPresenter {
    fun present(state: MeasurementRunState): MeasurementRunUiModel {
        val complete = state.progress == MeasurementRunProgress.END || state.progress == MeasurementRunProgress.ERROR
        val header = when (state.progress) {
            MeasurementRunProgress.PRE,
            MeasurementRunProgress.START -> "Measuring"
            MeasurementRunProgress.LOCATE -> "Finding server"
            MeasurementRunProgress.LATENCY -> "Measuring latency"
            MeasurementRunProgress.DOWNLOAD -> "Measuring download speed"
            MeasurementRunProgress.UPLOAD -> "Measuring upload speed"
            MeasurementRunProgress.END -> "Measurement complete"
            MeasurementRunProgress.ERROR -> {
                val base = "Measurement failed"
                val msg = state.errorMessage
                if (msg.isNullOrBlank()) base else "$base: $msg"
            }
        }
        val key = when (state.progress) {
            MeasurementRunProgress.PRE,
            MeasurementRunProgress.START -> MeasurementRunHeaderKey.MEASURING
            MeasurementRunProgress.LOCATE -> MeasurementRunHeaderKey.FINDING_SERVER
            MeasurementRunProgress.LATENCY -> MeasurementRunHeaderKey.MEASURING_LATENCY
            MeasurementRunProgress.DOWNLOAD -> MeasurementRunHeaderKey.MEASURING_DOWNLOAD
            MeasurementRunProgress.UPLOAD -> MeasurementRunHeaderKey.MEASURING_UPLOAD
            MeasurementRunProgress.END -> MeasurementRunHeaderKey.MEASUREMENT_COMPLETE
            MeasurementRunProgress.ERROR -> MeasurementRunHeaderKey.MEASUREMENT_FAILED
        }
        return MeasurementRunUiModel(
            headerKey = key,
            headerText = header,
            showProgressBar = !complete,
            showCompletionActions = complete,
        )
    }
}

/**
 * Shared reducer that mirrors legacy MeasureViewModel state transitions without platform bindings.
 */
class MeasurementRunViewController {
    private var state = MeasurementRunState()

    fun currentState(): MeasurementRunState = state

    fun reset(): MeasurementRunState {
        state = MeasurementRunState()
        return state
    }

    fun setInVehicle(value: Boolean): MeasurementRunState {
        state = state.copy(inVehicle = value)
        return state
    }

    fun onSequenceStarted(groupId: String): MeasurementRunState {
        state = state.copy(
            progress = MeasurementRunProgress.START,
            results = MeasurementGroup(
                latency = null,
                download = null,
                upload = null,
                submission = null,
                id = groupId,
            ),
            errorMessage = null,
        )
        return state
    }

    fun onLocateStarted(): MeasurementRunState = updateProgress(MeasurementRunProgress.LOCATE)

    fun onLatencyStarted(): MeasurementRunState = updateProgress(MeasurementRunProgress.LATENCY)

    fun onDownloadStarted(): MeasurementRunState = updateProgress(MeasurementRunProgress.DOWNLOAD)

    fun onUploadStarted(): MeasurementRunState = updateProgress(MeasurementRunProgress.UPLOAD)

    fun onLatencyMeasured(measurement: Measurement): MeasurementRunState {
        state = state.copy(
            results = MeasurementGroup(
                latency = measurement,
                download = state.results?.download,
                upload = state.results?.upload,
                submission = state.results?.submission,
            ),
        )
        return state
    }

    fun onThroughputMeasured(measurement: Measurement): MeasurementRunState {
        state = state.copy(
            results = when (measurement.type) {
                "download" -> MeasurementGroup(
                    latency = state.results?.latency,
                    download = measurement,
                    upload = state.results?.upload,
                    submission = state.results?.submission,
                )

                "upload" -> MeasurementGroup(
                    latency = state.results?.latency,
                    download = state.results?.download,
                    upload = measurement,
                    submission = state.results?.submission,
                )

                else -> error("throughput complete called with non-throughput measurement: ${measurement.type}")
            },
        )
        return state
    }

    fun onCompleted(group: MeasurementGroup?, errorCode: Int?, errorText: String?): MeasurementRunState {
        if (group == null) {
            val msg = when (errorCode) {
                429 -> RATE_LIMITED_ERROR_MESSAGE
                else -> errorText ?: DEFAULT_ERROR_MESSAGE
            }
            state = state.copy(
                progress = MeasurementRunProgress.ERROR,
                errorMessage = msg,
            )
            return state
        }

        state = state.copy(
            progress = MeasurementRunProgress.END,
            results = group,
            errorMessage = null,
        )
        return state
    }

    fun onUploadTimeResolved(uploadTime: Instant?): MeasurementRunState {
        state = state.copy(uploadTime = uploadTime)
        return state
    }

    private fun updateProgress(progress: MeasurementRunProgress): MeasurementRunState {
        state = state.copy(progress = progress)
        return state
    }

    companion object {
        const val DEFAULT_ERROR_MESSAGE = "Measurement failed. Please try again."
        const val RATE_LIMITED_ERROR_MESSAGE =
            "You’re temporarily rate-limited by the test servers. Please wait a few minutes and try again."
    }
}
