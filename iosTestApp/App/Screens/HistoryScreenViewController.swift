import UIKit
import sharedKit

/// Saved runs and what sync has done with them. Mirror of `HistoryScreen` on
/// Android, on the same shared `MeasurementHistoryViewModel`.
final class HistoryScreenViewController: UIViewController {

    private let viewModel: MeasurementHistoryViewModel
    private let snapshotProvider: (@escaping (ProductHistorySnapshot) -> Void) -> Void
    private let onRetry: (@escaping (ProductHistorySnapshot) -> Void) -> Void
    private let onExport: () -> Void
    private let onBack: () -> Void

    private let scaffold = ListScreenScaffold()
    private let header = Components.sectionHeader("")
    private let syncCard = Components.StatusCardView()
    private let detailHeader = Components.sectionHeader("")
    private let detailText = Components.bodyText("")
    /// Always present, never hidden. It used to disappear whenever the queue
    /// was empty, which reads as "this screen cannot sync" rather than "there
    /// is nothing to sync".
    private lazy var syncButton = Components.primaryButton(HistoryCopy.shared.SYNC_NOW)
    private var hasPendingUploads = false
    private lazy var exportButton = Components.secondaryButton(HistoryCopy.shared.EXPORT_DATA)
    private lazy var backButton = Components.secondaryButton(MapHomeCopy.shared.BACK_TO_MAP)

    /// Rows are rebuilt per render, so their targets need a stable owner.
    private var rowTimestamps: [UIButton: Int64] = [:]

    init(
        viewModel: MeasurementHistoryViewModel,
        snapshotProvider: @escaping (@escaping (ProductHistorySnapshot) -> Void) -> Void,
        onRetry: @escaping (@escaping (ProductHistorySnapshot) -> Void) -> Void,
        onExport: @escaping () -> Void,
        onBack: @escaping () -> Void
    ) {
        self.viewModel = viewModel
        self.snapshotProvider = snapshotProvider
        self.onRetry = onRetry
        self.onExport = onExport
        self.onBack = onBack
        super.init(nibName: nil, bundle: nil)
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    override func loadView() { view = scaffold }

    override func viewDidLoad() {
        super.viewDidLoad()

        syncButton.addTarget(self, action: #selector(retryTapped), for: .touchUpInside)
        exportButton.addTarget(self, action: #selector(exportTapped), for: .touchUpInside)
        backButton.addTarget(self, action: #selector(backTapped), for: .touchUpInside)

        // Selected run above the list, not below it. Below, choosing a row
        // scrolled its own detail off the bottom, and the more history a user
        // had the further away the answer moved.
        scaffold.addHeader(
            header,
            syncCard,
            detailHeader,
            detailText,
            Components.divider()
        )
        scaffold.addActions(syncButton, exportButton, backButton)
        render(viewModel.currentState())
    }

    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        // Reloaded on every appearance: returning from a run is exactly when
        // there is a new row to show.
        snapshotProvider { [weak self] snapshot in self?.apply(snapshot) }
    }

    private func apply(_ snapshot: ProductHistorySnapshot) {
        _ = viewModel.onRunsLoaded(runs: snapshot.runs)
        render(viewModel.onSyncStatusLoaded(
            summary: snapshot.syncSummary,
            pendingMeasurements: snapshot.pendingMeasurements,
            pendingSubmissions: snapshot.pendingSubmissions
        ))
    }

    private func render(_ state: MeasurementHistoryUiState) {
        header.text = state.headerText
        syncCard.update(
            [state.syncHeadline, state.syncDetail].compactMap { $0 }.joined(separator: " "),
            tone: state.hasPendingUploads ? .warning : .success
        )
        detailHeader.text = state.selectedTitle
        detailText.text = state.selectedDetail

        hasPendingUploads = state.hasPendingUploads
        syncButton.isEnabled = true
        syncButton.setTitle(HistoryCopy.shared.SYNC_NOW, for: .normal)

        rowTimestamps.removeAll()

        if state.isEmpty {
            scaffold.setListItems([Components.emptyState(message: state.emptyText ?? "")])
            return
        }
        var rows: [UIView] = []
        for row in state.runRows {
            let button = Components.secondaryButton(row.summary)
            button.titleLabel?.numberOfLines = 0
            button.titleLabel?.textAlignment = .left
            button.contentHorizontalAlignment = .leading
            if row.selected {
                button.layer.borderWidth = 2
                button.layer.borderColor = Theme.Color.primary.cgColor
            }
            button.addTarget(self, action: #selector(rowTapped(_:)), for: .touchUpInside)
            rowTimestamps[button] = row.timestampMs
            rows.append(button)
        }
        scaffold.setListItems(rows)
    }

    @objc private func rowTapped(_ sender: UIButton) {
        guard let timestamp = rowTimestamps[sender] else { return }
        render(viewModel.onRunSelected(timestampMs: timestamp))
    }

    @objc private func retryTapped() {
        // Nothing queued means the answer is already known; a round trip
        // would only make the user wait to be told so.
        guard hasPendingUploads else {
            syncCard.update(HistoryCopy.shared.NOTHING_TO_SYNC, tone: .success)
            return
        }
        syncButton.isEnabled = false
        syncButton.setTitle(HistoryCopy.shared.SYNCING, for: .normal)
        onRetry { [weak self] snapshot in self?.apply(snapshot) }
    }

    @objc private func exportTapped() { onExport() }

    @objc private func backTapped() { onBack() }
}
