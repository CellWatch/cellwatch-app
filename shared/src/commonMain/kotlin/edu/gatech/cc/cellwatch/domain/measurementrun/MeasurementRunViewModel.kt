package edu.gatech.cc.cellwatch.domain.measurementrun

import edu.gatech.cc.cellwatch.core.util.SharedLog
import edu.gatech.cc.cellwatch.domain.app.ProductContainer
import edu.gatech.cc.cellwatch.domain.fcc.FccSubmissionOutcomeMessage
import edu.gatech.cc.cellwatch.domain.fcc.MeasurementFailureMessage
import edu.gatech.cc.cellwatch.domain.fcc.MeasurementSequenceProgressListener
import edu.gatech.cc.cellwatch.domain.fcc.MeasurementSequenceRequest
import edu.gatech.cc.cellwatch.domain.fcc.MeasurementSequenceStage
import edu.gatech.cc.cellwatch.domain.model.CollectionMode
import edu.gatech.cc.cellwatch.domain.sync.MeasurementRunHandle
import com.benasher44.uuid.uuid4
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Everything a run or results screen renders, in one immutable object.
 *
 * Both screens read the same state because they are the same run at different
 * progress values; splitting them would mean two sources of truth for one
 * measurement.
 */
data class MeasurementRunUiState(
    val progress: MeasurementRunProgress,
    val headerText: String,
    val showProgressBar: Boolean,
    /** 0-100. Shared so both platforms advance the bar identically. */
    val progressPercent: Int,
    val showCompletionActions: Boolean,
    val latencyText: String,
    val downloadText: String,
    val uploadText: String,
    val uploadedText: String,
    val summaryText: String,
    /**
     * Whether this measurement reaches the FCC, and if not, why. Empty until
     * the run finishes - there is nothing to say while it is still going.
     */
    val fccOutcomeText: String,
    val groupId: String?,
) {
    val isError: Boolean get() = progress == MeasurementRunProgress.ERROR

    /**
     * A run the user stopped is not a failure. Screens colour it neutrally so
     * cancelling does not look like something broke.
     */
    val isCancellation: Boolean get() = isError && MeasurementFailureMessage.isCancellation(summaryText)
}

/**
 * The single door for the measurement run and results screens.
 *
 * Wraps [MeasurementRunViewController] (the reducer), [MeasurementRunUiPresenter]
 * and [MeasurementResultReadModelUseCase], which screens previously would have
 * had to orchestrate themselves - the mistake that produced three subtly
 * different iOS harnesses.
 *
 * State is delivered by callback rather than a Flow: Swift consumes this
 * directly, and a callback crosses the ObjC interop boundary without a
 * collector.
 */
class MeasurementRunViewModel(
    private val container: ProductContainer,
    private val mode: CollectionMode,
    private val inVehicle: Boolean,
) {
    /**
     * Owned rather than injected: Kotlin default arguments do not bridge to
     * Swift, so a defaulted scope parameter would force every Swift call site
     * to construct one, and they would not agree.
     */
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val controller = MeasurementRunViewController()
    private val presenter = MeasurementRunUiPresenter()
    private val readModel = MeasurementResultReadModelUseCase()
    private var handle: MeasurementRunHandle? = null
    private var fccOutcomeText: String = ""

    init {
        controller.setInVehicle(inVehicle)
    }

    fun currentState(): MeasurementRunUiState = project()

    val isRunning: Boolean get() = handle?.isRunning == true

    /**
     * Starts the sequence. [onState] is invoked on every transition, including
     * the terminal one, so a screen renders by assignment rather than by
     * tracking which stage it last saw.
     */
    fun start(onState: (MeasurementRunUiState) -> Unit): MeasurementRunHandle {
        val groupId = uuid4().toString()
        val orchestratorProgress = MeasurementSequenceProgressListener { stage ->
            when (stage) {
                MeasurementSequenceStage.STARTED -> controller.onSequenceStarted(groupId)
                MeasurementSequenceStage.LOCATE -> controller.onLocateStarted()
                MeasurementSequenceStage.LATENCY -> controller.onLatencyStarted()
                MeasurementSequenceStage.DOWNLOAD -> controller.onDownloadStarted()
                MeasurementSequenceStage.UPLOAD -> controller.onUploadStarted()
                MeasurementSequenceStage.DONE -> Unit
            }
            onState(project())
        }

        val job = scope.launch {
            try {
                // Resolved here rather than at construction: it reads the
                // persisted device credential, which is a suspend call.
                val submissionProfile = container.submissionProfile()
                val request = MeasurementSequenceRequest(
                    groupId = groupId,
                    inVehicle = inVehicle,
                    mode = mode,
                    measurementId = null,
                    submissionProfile = submissionProfile,
                )
                val outcome = container
                    .createSequenceSyncOrchestrator(submissionProfile, orchestratorProgress)
                    .run(request)
                fccOutcomeText = FccSubmissionOutcomeMessage.forOutcome(
                    submissionCreated = outcome.sequenceOutcome.group.submission != null,
                    validation = outcome.sequenceOutcome.submissionValidation,
                )
                controller.onCompleted(outcome.sequenceOutcome.group, null, null)
                controller.onUploadTimeResolved(outcome.measurementCompleteUploadTime)
            } catch (cancellation: CancellationException) {
                // Reported as a cancellation rather than rethrown: the screen
                // still has to tell the user why the run stopped.
                controller.onCompleted(null, null, MeasurementFailureMessage.CANCELLED)
            } catch (error: Throwable) {
                // The raw text goes into the reducer so errorCategory still
                // classifies correctly; project() humanises it for display.
                SharedLog.w(TAG, "measurement sequence failed: ${error.message}")
                controller.onCompleted(null, null, error.toString())
            }
            onState(project())
        }
        return MeasurementRunHandle(job).also { handle = it }
    }

    /** Stops a run in progress. Safe to call when nothing is running. */
    fun cancel() {
        handle?.cancel()
    }

    private fun project(): MeasurementRunUiState {
        val raw = controller.currentState()
        // Humanised here rather than in the reducer: the reducer's raw text is
        // what errorCategory classifies from, and what belongs in a log, but
        // "MsakException: authorize call failed" means nothing to a user.
        val state = raw.errorMessage
            ?.let { raw.copy(errorMessage = MeasurementFailureMessage.forFailure(it)) }
            ?: raw
        val ui = presenter.present(state)
        val result = readModel.present(state)
        return MeasurementRunUiState(
            progress = state.progress,
            headerText = ui.headerText,
            showProgressBar = ui.showProgressBar,
            progressPercent = percentFor(state.progress),
            showCompletionActions = ui.showCompletionActions,
            latencyText = result.latencyText,
            downloadText = result.downloadText,
            uploadText = result.uploadText,
            uploadedText = result.uploadedText,
            summaryText = result.summaryText,
            fccOutcomeText = fccOutcomeText,
            groupId = state.results?.id,
        )
    }

    private companion object {
        const val TAG = "MeasurementRunViewModel"

        /**
         * Stage weights, not equal fifths: locating a server is quick and the
         * two throughput tests dominate, so equal steps would stall visibly on
         * download and race through the rest.
         */
        fun percentFor(progress: MeasurementRunProgress): Int = when (progress) {
            MeasurementRunProgress.PRE -> 0
            MeasurementRunProgress.START -> 5
            MeasurementRunProgress.LOCATE -> 15
            MeasurementRunProgress.LATENCY -> 35
            MeasurementRunProgress.DOWNLOAD -> 60
            MeasurementRunProgress.UPLOAD -> 85
            MeasurementRunProgress.END,
            MeasurementRunProgress.ERROR -> 100
        }
    }
}
