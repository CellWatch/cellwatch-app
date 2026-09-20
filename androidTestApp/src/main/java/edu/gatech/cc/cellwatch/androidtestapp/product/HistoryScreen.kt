package edu.gatech.cc.cellwatch.androidtestapp.product

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import edu.gatech.cc.cellwatch.androidtestapp.designsystem.Components
import edu.gatech.cc.cellwatch.androidtestapp.designsystem.ListScreenScaffold
import edu.gatech.cc.cellwatch.androidtestapp.designsystem.Theme
import edu.gatech.cc.cellwatch.androidtestapp.designsystem.Theme.dp
import edu.gatech.cc.cellwatch.domain.app.ProductHistorySnapshot
import edu.gatech.cc.cellwatch.domain.maphome.MapHomeCopy
import edu.gatech.cc.cellwatch.domain.measurementhistory.HistoryCopy
import edu.gatech.cc.cellwatch.domain.measurementhistory.MeasurementHistoryUiState
import edu.gatech.cc.cellwatch.domain.measurementhistory.MeasurementHistoryViewModel

/**
 * Saved runs and what sync has done with them. Android counterpart of
 * `HistoryScreenViewController`.
 *
 * Both platforms previously hand-rolled this list beside an unused shared
 * controller; this one renders whatever [MeasurementHistoryViewModel] hands it.
 */
class HistoryScreen(
    private val context: Context,
    private val viewModel: MeasurementHistoryViewModel,
    private val snapshotProvider: ((ProductHistorySnapshot) -> Unit) -> Unit,
    private val onRetry: ((ProductHistorySnapshot) -> Unit) -> Unit,
) {

    private val scaffold = ListScreenScaffold(context)
    private val header = Components.sectionHeader(context, "")
    private val syncCard = Components.StatusCardView(context)
    private val detailHeader = Components.sectionHeader(context, "")
    private val detailText = Components.bodyText(context, "")
    /**
     * Always present, never hidden.
     *
     * It used to disappear whenever the queue was empty, which reads as "this
     * screen cannot sync" rather than "there is nothing to sync". With
     * nothing queued it answers immediately rather than making a pointless
     * round trip.
     */
    private val syncButton = Components.primaryButton(context, HistoryCopy.SYNC_NOW).apply {
        setOnClickListener {
            if (!hasPendingUploads) {
                syncCard.update(HistoryCopy.NOTHING_TO_SYNC, Components.StatusTone.SUCCESS)
                return@setOnClickListener
            }
            isEnabled = false
            text = HistoryCopy.SYNCING
            onRetry { snapshot -> apply(snapshot) }
        }
    }
    private var hasPendingUploads = false
    private val exportButton = Components.secondaryButton(context, HistoryCopy.EXPORT_DATA)
    private val backButton = Components.secondaryButton(context, MapHomeCopy.BACK_TO_MAP)

    val view: View get() = scaffold

    init {
        // Selected run above the list, not below it. Below, choosing a row
        // scrolled its own detail off the bottom, and the more history a user
        // had the further away the answer moved.
        scaffold.addHeader(
            header,
            syncCard,
            detailHeader,
            detailText,
            Components.divider(context),
        )
        scaffold.addActions(syncButton, exportButton, backButton)
        render(viewModel.currentState())
        refresh()
    }

    fun setOnBack(action: () -> Unit) {
        backButton.setOnClickListener { action() }
    }

    fun setOnExport(action: () -> Unit) {
        exportButton.setOnClickListener { action() }
    }

    /** Re-read on every appearance, so a new run shows without a restart. */
    fun refresh() {
        snapshotProvider { snapshot -> apply(snapshot) }
    }

    private fun apply(snapshot: ProductHistorySnapshot) {
        viewModel.onRunsLoaded(snapshot.runs)
        render(
            viewModel.onSyncStatusLoaded(
                summary = snapshot.syncSummary,
                pendingMeasurements = snapshot.pendingMeasurements,
                pendingSubmissions = snapshot.pendingSubmissions,
            ),
        )
    }

    private fun render(state: MeasurementHistoryUiState) {
        header.text = state.headerText
        hasPendingUploads = state.hasPendingUploads
        syncCard.update(
            listOfNotNull(state.syncHeadline, state.syncDetail).joinToString(" "),
            if (state.hasPendingUploads) Components.StatusTone.WARNING else Components.StatusTone.SUCCESS,
        )
        detailHeader.text = state.selectedTitle
        detailText.text = state.selectedDetail

        syncButton.isEnabled = true
        syncButton.text = HistoryCopy.SYNC_NOW

        if (state.isEmpty) {
            scaffold.setListItems(listOf(Components.emptyState(context, state.emptyText.orEmpty())))
            return
        }
        scaffold.setListItems(
            state.runRows.map { row ->
                Components.listRow(
                    context = context,
                    title = row.summary,
                    subtitle = null,
                    accessory = if (row.selected) HistoryCopy.SELECTED else null,
                ).apply {
                    setOnClickListener { render(viewModel.onRunSelected(row.timestampMs)) }
                }
            },
        )
    }
}
