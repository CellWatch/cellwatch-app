package edu.gatech.cc.cellwatch.androidtestapp.product

import android.app.Activity
import android.content.Context
import android.view.View
import android.view.WindowManager
import edu.gatech.cc.cellwatch.androidtestapp.designsystem.Components
import edu.gatech.cc.cellwatch.androidtestapp.designsystem.ScreenScaffold
import edu.gatech.cc.cellwatch.domain.app.ShellCopy
import edu.gatech.cc.cellwatch.domain.fcc.FccSubmissionOutcomeMessage
import edu.gatech.cc.cellwatch.domain.measurementrun.MeasurementRunCopy
import edu.gatech.cc.cellwatch.domain.measurementrun.MeasurementRunProgress
import edu.gatech.cc.cellwatch.domain.measurementrun.MeasurementRunUiState
import edu.gatech.cc.cellwatch.domain.measurementrun.MeasurementRunViewModel
import edu.gatech.cc.cellwatch.domain.measurementstart.MeasurementStartCopy

/**
 * The run and its results, in one screen. Android counterpart of
 * `MeasurementRunScreenViewController`.
 *
 * One screen rather than two because the shared [MeasurementRunUiState] already
 * describes both - `showProgressBar` while measuring, `showCompletionActions`
 * once finished - and a separate results screen would need a second copy of the
 * same run to render.
 *
 * The run starts on construction: the user already pressed Start on the
 * previous screen, and asking twice would be a dead step.
 */
class MeasurementRunScreen(
    private val context: Context,
    private val viewModel: MeasurementRunViewModel,
    private val onDone: () -> Unit,
    private val onMeasureAgain: () -> Unit,
) {

    private val scaffold = ScreenScaffold(context)
    private val progressHeader = Components.ProgressHeaderView(context)
    private val statusCard = Components.StatusCardView(context)
    private val syncCard = Components.StatusCardView(context)
    private val fccCard = Components.StatusCardView(context)
    private val latencyRow = Components.MetricRowView(context, MeasurementRunCopy.LATENCY)
    private val downloadRow = Components.MetricRowView(context, MeasurementRunCopy.DOWNLOAD)
    private val uploadRow = Components.MetricRowView(context, MeasurementRunCopy.UPLOAD)
    private val uploadedRow = Components.MetricRowView(context, MeasurementRunCopy.SYNC)

    private val cancelButton = Components.secondaryButton(context, MeasurementStartCopy.STOP_MEASUREMENT).apply {
        setOnClickListener { viewModel.cancel() }
    }
    private val doneButton = Components.primaryButton(context, ShellCopy.DONE).apply {
        setOnClickListener { onDone() }
    }
    private val againButton = Components.secondaryButton(context, MeasurementRunCopy.MEASURE_AGAIN).apply {
        setOnClickListener { onMeasureAgain() }
    }

    val view: View get() = scaffold

    init {
        scaffold.addContent(
            progressHeader,
            Components.divider(context),
            latencyRow,
            downloadRow,
            uploadRow,
            uploadedRow,
            Components.divider(context),
            statusCard,
            syncCard,
            fccCard,
        )
        scaffold.addActions(cancelButton, doneButton, againButton)

        render(viewModel.currentState())
        keepScreenOn(true)
        // Callbacks arrive on the sequence's background dispatcher, so hop to
        // the main thread before touching views.
        viewModel.start { state -> scaffold.post { render(state) } }
    }

    /** Releases the screen-awake lock. The host calls this when leaving. */
    fun onDestroy() {
        viewModel.cancel()
        keepScreenOn(false)
    }

    private fun keepScreenOn(enabled: Boolean) {
        val window = (context as? Activity)?.window ?: return
        if (enabled) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun render(state: MeasurementRunUiState) {
        progressHeader.update(state.headerText, state.progressPercent)
        latencyRow.update(state.latencyText)
        downloadRow.update(state.downloadText)
        uploadRow.update(state.uploadText)
        uploadedRow.update(state.uploadedText)
        statusCard.update(state.summaryText, toneFor(state))

        // Same reason as the FCC card: there is no sync story until the run
        // finishes, and MeasurementRunCopy.syncLabel(MeasurementRunCopy.PENDING) on its own never said when anything
        // last reached the server, or whether uploads were failing.
        syncCard.visibility = if (state.syncDetailText.isBlank()) View.GONE else View.VISIBLE
        syncCard.update(state.syncDetailText, Components.StatusTone.NEUTRAL)

        // Hidden mid-run: whether a measurement reaches the FCC is not known
        // until it finishes, and guessing early would be worse than silence.
        fccCard.visibility = if (state.fccOutcomeText.isBlank()) View.GONE else View.VISIBLE
        fccCard.update(
            state.fccOutcomeText,
            if (state.fccOutcomeText == FccSubmissionOutcomeMessage.SUBMITTED) {
                Components.StatusTone.SUCCESS
            } else {
                Components.StatusTone.WARNING
            },
        )

        cancelButton.visibility = if (state.showCompletionActions) View.GONE else View.VISIBLE
        doneButton.visibility = if (state.showCompletionActions) View.VISIBLE else View.GONE
        againButton.visibility = if (state.showCompletionActions) View.VISIBLE else View.GONE

        if (state.showCompletionActions) keepScreenOn(false)
    }

    private fun toneFor(state: MeasurementRunUiState): Components.StatusTone = when {
        // A run the user stopped is not a failure, so it is not coloured like one.
        state.isCancellation -> Components.StatusTone.NEUTRAL
        state.isError -> Components.StatusTone.WARNING
        state.progress == MeasurementRunProgress.END -> Components.StatusTone.SUCCESS
        else -> Components.StatusTone.NEUTRAL
    }

}
