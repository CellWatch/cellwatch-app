package edu.gatech.cc.cellwatch.domain.measurementrun

import edu.gatech.cc.cellwatch.domain.fcc.MeasurementFailureMessage
import edu.gatech.cc.cellwatch.domain.model.Measurement
import edu.gatech.cc.cellwatch.domain.model.MeasurementGroup
import kotlin.math.roundToInt
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
    /**
     * Variation in round-trip time, and how many probes never came back.
     *
     * MSAK reports both and the app has always stored them; nothing showed
     * them. On cellular they are often the interesting part - a 40 ms mean
     * with 15% loss is a worse connection than a steady 90 ms, and the mean
     * alone cannot say so. frozenApp did not surface these either.
     */
    val jitterText: String,
    val packetLossText: String,
    val downloadText: String,
    val uploadText: String,
    val uploadedText: String,
    val summaryText: String,
)

class MeasurementResultReadModelUseCase {
    fun present(state: MeasurementRunState): MeasurementResultReadModel {
        val latencyText = formatLatency(state.results?.latency)
        val downloadText = formatThroughput(state.results?.download)
        val uploadText = formatThroughput(state.results?.upload)
        val uploadedText = when (state.progress) {
            MeasurementRunProgress.END ->
                if (state.uploadTime != null) MeasurementRunCopy.UPLOADED else MeasurementRunCopy.PENDING_SYNC
            MeasurementRunProgress.ERROR -> MeasurementRunCopy.NOT_UPLOADED
            else -> MeasurementRunCopy.IN_PROGRESS
        }
        val summaryText = when (state.progress) {
            MeasurementRunProgress.END -> {
                if (state.uploadTime != null) {
                    MeasurementRunCopy.COMPLETE_AND_SYNCED
                } else {
                    MeasurementRunCopy.COMPLETE_SYNC_ATTEMPTED
                }
            }

            MeasurementRunProgress.ERROR -> state.errorMessage ?: MeasurementRunCopy.FAILED_TRY_AGAIN
            else -> MeasurementRunCopy.IN_PROGRESS_SENTENCE
        }
        return MeasurementResultReadModel(
            latencyText = latencyText,
            jitterText = formatJitter(state.results?.latency),
            packetLossText = formatPacketLoss(state.results?.latency),
            downloadText = downloadText,
            uploadText = uploadText,
            uploadedText = uploadedText,
            summaryText = summaryText,
        )
    }

    private fun formatLatency(measurement: Measurement?): String {
        val rttMicros = measurement?.latencyData?.rtt ?: return MeasurementRunCopy.NO_VALUE
        val milliseconds = rttMicros / 1_000.0
        if (milliseconds < 1.0) return MeasurementRunCopy.SUB_MILLISECOND
        return "${milliseconds.roundToInt()} ms"
    }

    private fun formatJitter(measurement: Measurement?): String {
        val micros = measurement?.latencyData?.jitter ?: return MeasurementRunCopy.NO_VALUE
        val milliseconds = micros / 1_000.0
        if (milliseconds < 1.0) return MeasurementRunCopy.SUB_MILLISECOND
        return "${milliseconds.roundToInt()} ms"
    }

    /**
     * Loss as a percentage, with the counts behind it.
     *
     * The counts are shown because the percentage alone hides sample size:
     * "50%" reads as a catastrophe when it is one probe of two, and as a fact
     * when it is 66 of 132.
     */
    private fun formatPacketLoss(measurement: Measurement?): String {
        val latency = measurement?.latencyData ?: return MeasurementRunCopy.NO_VALUE
        val sent = latency.sent ?: return MeasurementRunCopy.NO_VALUE
        val received = latency.received ?: return MeasurementRunCopy.NO_VALUE
        if (sent <= 0) return MeasurementRunCopy.NO_VALUE
        val lost = (sent - received).coerceAtLeast(0)
        val percent = (lost * 1_000.0 / sent).roundToInt() / 10.0
        val percentText = if (percent % 1.0 == 0.0) "${percent.toInt()}" else "$percent"
        return MeasurementRunCopy.packetLoss(percentText, lost, sent)
    }

    private fun formatThroughput(measurement: Measurement?): String {
        val data = measurement?.uploadDownloadData ?: return MeasurementRunCopy.NO_VALUE
        val bytesPerSec = data.bytesPerSec ?: run {
            val bytes = data.bytes
            val durationMicros = data.duration
            if (bytes == null || durationMicros == null || durationMicros <= 0L) {
                return MeasurementRunCopy.NO_VALUE
            }
            bytes.toDouble() / (durationMicros.toDouble() / 1_000_000.0)
        }
        val mbps = (bytesPerSec * 8.0) / 1_000_000.0
        if (mbps < 0.1) return MeasurementRunCopy.SUB_TENTH_MBPS
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
            MeasurementRunProgress.START -> MeasurementRunCopy.MEASURING
            MeasurementRunProgress.LOCATE -> MeasurementRunCopy.FINDING_SERVER
            MeasurementRunProgress.LATENCY -> MeasurementRunCopy.MEASURING_LATENCY
            MeasurementRunProgress.DOWNLOAD -> MeasurementRunCopy.MEASURING_DOWNLOAD
            MeasurementRunProgress.UPLOAD -> MeasurementRunCopy.MEASURING_UPLOAD
            MeasurementRunProgress.END -> MeasurementRunCopy.MEASUREMENT_COMPLETE
            MeasurementRunProgress.ERROR -> {
                val msg = state.errorMessage
                when {
                    msg.isNullOrBlank() -> MeasurementRunCopy.MEASUREMENT_FAILED
                    // A run the user stopped is not a failure, and reading
                    // "Measurement failed: The measurement was cancelled" back
                    // to them is both redundant and wrong.
                    MeasurementFailureMessage.isCancellation(msg) -> MeasurementRunCopy.MEASUREMENT_CANCELLED
                    else -> MeasurementRunCopy.measurementFailed(msg)
                }
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
