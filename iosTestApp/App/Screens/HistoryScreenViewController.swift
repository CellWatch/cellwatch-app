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

    private let scaffold = ScreenScaffold()
    private let header = Components.sectionHeader("")
    private let syncCard = Components.StatusCardView()
    private let runsStack = UIStackView()
    private let detailHeader = Components.sectionHeader("")
    private let detailText = Components.bodyText("")
    private lazy var retryButton = Components.primaryButton("Retry upload")
    private lazy var exportButton = Components.secondaryButton("Export data")
    private lazy var backButton = Components.secondaryButton("Back to map")

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
        runsStack.axis = .vertical
        runsStack.spacing = Theme.Space.s

        retryButton.addTarget(self, action: #selector(retryTapped), for: .touchUpInside)
        exportButton.addTarget(self, action: #selector(exportTapped), for: .touchUpInside)
        backButton.addTarget(self, action: #selector(backTapped), for: .touchUpInside)

        scaffold.addContent(
            header,
            syncCard,
            Components.divider(),
            runsStack,
            Components.divider(),
            detailHeader,
            detailText
        )
        scaffold.addActions(retryButton, exportButton, backButton)
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
            tone: state.showRetry ? .warning : .success
        )
        detailHeader.text = state.selectedTitle
        detailText.text = state.selectedDetail

        retryButton.isHidden = !state.showRetry
        retryButton.isEnabled = true
        retryButton.setTitle("Retry upload", for: .normal)

        runsStack.arrangedSubviews.forEach { $0.removeFromSuperview() }
        rowTimestamps.removeAll()

        if state.isEmpty {
            runsStack.addArrangedSubview(Components.emptyState(message: state.emptyText ?? ""))
            return
        }
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
            runsStack.addArrangedSubview(button)
        }
    }

    @objc private func rowTapped(_ sender: UIButton) {
        guard let timestamp = rowTimestamps[sender] else { return }
        render(viewModel.onRunSelected(timestampMs: timestamp))
    }

    @objc private func retryTapped() {
        retryButton.isEnabled = false
        retryButton.setTitle("Retrying…", for: .normal)
        onRetry { [weak self] snapshot in self?.apply(snapshot) }
    }

    @objc private func exportTapped() { onExport() }

    @objc private func backTapped() { onBack() }
}
