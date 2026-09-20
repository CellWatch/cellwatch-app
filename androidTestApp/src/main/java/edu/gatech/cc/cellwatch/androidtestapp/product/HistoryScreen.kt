package edu.gatech.cc.cellwatch.androidtestapp.product

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import edu.gatech.cc.cellwatch.androidtestapp.designsystem.Components
import edu.gatech.cc.cellwatch.androidtestapp.designsystem.ScreenScaffold
import edu.gatech.cc.cellwatch.androidtestapp.designsystem.Theme
import edu.gatech.cc.cellwatch.androidtestapp.designsystem.Theme.dp
import edu.gatech.cc.cellwatch.domain.app.ProductHistorySnapshot
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

    private val scaffold = ScreenScaffold(context)
    private val header = Components.sectionHeader(context, "")
    private val syncCard = Components.StatusCardView(context)
    private val runsColumn = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val detailHeader = Components.sectionHeader(context, "")
    private val detailText = Components.bodyText(context, "")
    private val retryButton = Components.primaryButton(context, "Retry upload").apply {
        setOnClickListener {
            isEnabled = false
            text = "Retrying…"
            onRetry { snapshot -> apply(snapshot) }
        }
    }
    private val exportButton = Components.secondaryButton(context, "Export data")
    private val backButton = Components.secondaryButton(context, "Back to map")

    val view: View get() = scaffold

    init {
        scaffold.addContent(
            header,
            syncCard,
            Components.divider(context),
            runsColumn,
            Components.divider(context),
            detailHeader,
            detailText,
        )
        scaffold.addActions(retryButton, exportButton, backButton)
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
        syncCard.update(
            listOfNotNull(state.syncHeadline, state.syncDetail).joinToString(" "),
            if (state.showRetry) Components.StatusTone.WARNING else Components.StatusTone.SUCCESS,
        )
        detailHeader.text = state.selectedTitle
        detailText.text = state.selectedDetail

        retryButton.visibility = if (state.showRetry) View.VISIBLE else View.GONE
        retryButton.isEnabled = true
        retryButton.text = "Retry upload"

        runsColumn.removeAllViews()
        if (state.isEmpty) {
            runsColumn.addView(Components.emptyState(context, state.emptyText.orEmpty()))
            return
        }
        state.runRows.forEach { row ->
            val listRow = Components.listRow(
                context = context,
                title = row.summary,
                subtitle = null,
                accessory = if (row.selected) "Selected" else null,
            )
            listRow.setOnClickListener { render(viewModel.onRunSelected(row.timestampMs)) }
            runsColumn.addView(
                listRow,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = context.dp(Theme.Space.S) },
            )
        }
    }
}
