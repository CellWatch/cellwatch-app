package edu.gatech.cc.cellwatch.domain.measurementrun

import edu.gatech.cc.cellwatch.domain.model.Measurement
import edu.gatech.cc.cellwatch.domain.model.MeasurementGroup
import kotlinx.datetime.Instant
import kotlin.math.roundToInt

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

enum class MeasurementRunErrorCategory {
    NONE,
    NETWORK,
    AUTH_CONFIG,
    SERVER,
    UNKNOWN,
}

data class MeasurementRunState(
    val progress: MeasurementRunProgress = MeasurementRunProgress.PRE,
    val results: MeasurementGroup? = null,
    val uploadTime: Instant? = null,
    val inVehicle: Boolean = false,
    val errorMessage: String? = null,
    val errorCategory: MeasurementRunErrorCategory = MeasurementRunErrorCategory.NONE,
)

data class MeasurementRunUiModel(
    val headerKey: MeasurementRunHeaderKey,
    val headerText: String,
    val showProgressBar: Boolean,
    val showCompletionActions: Boolean,
)

data class MeasurementResultReadModel(
    val latencyText: String,
    val downloadText: String,
    val uploadText: String,
    val summaryText: String,
)

class MeasurementResultReadModelUseCase {
    fun present(state: MeasurementRunState): MeasurementResultReadModel {
        val latencyText = formatLatency(state.results?.latency)
        val downloadText = formatThroughput(state.results?.download)
        val uploadText = formatThroughput(state.results?.upload)
        val summaryText = when (state.progress) {
            MeasurementRunProgress.END -> {
                if (state.uploadTime != null) {
                    "Measurement complete. Results saved and synced."
                } else {
                    "Measurement complete. Results saved and sync attempted."
                }
            }

            MeasurementRunProgress.ERROR -> state.errorMessage ?: "Measurement failed. Please try again."
            else -> "Measurement in progress."
        }
        return MeasurementResultReadModel(
            latencyText = latencyText,
            downloadText = downloadText,
            uploadText = uploadText,
            summaryText = summaryText,
        )
    }

    private fun formatLatency(measurement: Measurement?): String {
        val rttMicros = measurement?.latencyData?.rtt ?: return "--"
        val milliseconds = rttMicros / 1_000.0
        if (milliseconds < 1.0) return "<1 ms"
        return "${milliseconds.roundToInt()} ms"
    }

    private fun formatThroughput(measurement: Measurement?): String {
        val data = measurement?.uploadDownloadData ?: return "--"
        val bytesPerSec = data.bytesPerSec ?: run {
            val bytes = data.bytes
            val durationMicros = data.duration
            if (bytes == null || durationMicros == null || durationMicros <= 0L) {
                return "--"
            }
            bytes.toDouble() / (durationMicros.toDouble() / 1_000_000.0)
        }
        val mbps = (bytesPerSec * 8.0) / 1_000_000.0
        if (mbps < 0.1) return "<0.1 Mbps"
        val rounded = (mbps * 10.0).roundToInt() / 10.0
        return if ((rounded % 1.0) == 0.0) {
            "${rounded.toInt()} Mbps"
        } else {
            "$rounded Mbps"
        }
    }
}

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
            errorCategory = MeasurementRunErrorCategory.NONE,
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
                errorCategory = classifyErrorCategory(errorCode = errorCode, errorText = errorText),
            )
            return state
        }

        state = state.copy(
            progress = MeasurementRunProgress.END,
            results = group,
            errorMessage = null,
            errorCategory = MeasurementRunErrorCategory.NONE,
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

    private fun classifyErrorCategory(errorCode: Int?, errorText: String?): MeasurementRunErrorCategory {
        if (errorCode == 429) return MeasurementRunErrorCategory.SERVER
        val normalized = errorText.orEmpty().lowercase()
        if (normalized.isBlank()) return MeasurementRunErrorCategory.UNKNOWN
        if (
            "timeout" in normalized ||
            "timed out" in normalized ||
            "network" in normalized ||
            "unable to resolve host" in normalized ||
            "connection refused" in normalized ||
            "unreachable" in normalized
        ) {
            return MeasurementRunErrorCategory.NETWORK
        }
        if (
            "unauthorized" in normalized ||
            "forbidden" in normalized ||
            "invalid api key" in normalized ||
            "api key" in normalized ||
            "auth" in normalized ||
            "jwt" in normalized ||
            "permission denied" in normalized
        ) {
            return MeasurementRunErrorCategory.AUTH_CONFIG
        }
        if (
            "server" in normalized ||
            "protocol" in normalized ||
            "decode" in normalized ||
            "missingfieldexception" in normalized ||
            "http 5" in normalized
        ) {
            return MeasurementRunErrorCategory.SERVER
        }
        return MeasurementRunErrorCategory.UNKNOWN
    }

    companion object {
        const val DEFAULT_ERROR_MESSAGE = "Measurement failed. Please try again."
        const val RATE_LIMITED_ERROR_MESSAGE =
            "You’re temporarily rate-limited by the test servers. Please wait a few minutes and try again."
    }
}
