import UIKit
import sharedKit

/// The run and its results, in one screen. Mirror of `MeasurementRunScreen` on
/// Android.
///
/// One screen rather than two because the shared `MeasurementRunUiState`
/// already describes both — `showProgressBar` while measuring,
/// `showCompletionActions` once finished — and a separate results screen would
/// need a second copy of the same run to render.
///
/// The run starts on appear: the user already pressed Start on the previous
/// screen, and asking twice would be a dead step.
final class MeasurementRunScreenViewController: UIViewController {

    private let viewModel: MeasurementRunViewModel
    private let onDone: () -> Void
    private let onMeasureAgain: () -> Void

    private let scaffold = ScreenScaffold()
    private let progressHeader = Components.ProgressHeaderView()
    private let statusCard = Components.StatusCardView()
    private let syncCard = Components.StatusCardView()
    private let fccCard = Components.StatusCardView()
    private let latencyRow = Components.MetricRowView(label: "Latency")
    private let downloadRow = Components.MetricRowView(label: "Download")
    private let uploadRow = Components.MetricRowView(label: "Upload")
    private let syncRow = Components.MetricRowView(label: "Sync")

    private lazy var cancelButton = Components.secondaryButton("Stop measurement")
    private lazy var doneButton = Components.primaryButton("Done")
    private lazy var againButton = Components.secondaryButton("Measure again")

    private var didStart = false

    init(
        viewModel: MeasurementRunViewModel,
        onDone: @escaping () -> Void,
        onMeasureAgain: @escaping () -> Void
    ) {
        self.viewModel = viewModel
        self.onDone = onDone
        self.onMeasureAgain = onMeasureAgain
        super.init(nibName: nil, bundle: nil)
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    override func loadView() {
        view = scaffold
    }

    override func viewDidLoad() {
        super.viewDidLoad()

        cancelButton.addTarget(self, action: #selector(stopTapped), for: .touchUpInside)
        doneButton.addTarget(self, action: #selector(doneTapped), for: .touchUpInside)
        againButton.addTarget(self, action: #selector(againTapped), for: .touchUpInside)

        scaffold.addContent(
            progressHeader,
            Components.divider(),
            latencyRow,
            downloadRow,
            uploadRow,
            syncRow,
            Components.divider(),
            statusCard,
            syncCard,
            fccCard
        )
        scaffold.addActions(cancelButton, doneButton, againButton)

        render(viewModel.currentState())
    }

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        guard !didStart else { return }
        didStart = true

        // iOS has no foreground-service equivalent, so the run only survives
        // while the app is in front; keeping the screen awake stops an idle
        // lock from cancelling it mid-measurement.
        UIApplication.shared.isIdleTimerDisabled = true

        viewModel.start { [weak self] state in
            // Callbacks arrive on the sequence's background dispatcher.
            DispatchQueue.main.async { self?.render(state) }
        }
    }

    override func viewDidDisappear(_ animated: Bool) {
        super.viewDidDisappear(animated)
        // Leaving cancels: a measurement the user navigated away from would
        // produce partial data at best.
        viewModel.cancel()
        UIApplication.shared.isIdleTimerDisabled = false
    }

    private func render(_ state: MeasurementRunUiState) {
        progressHeader.update(state.headerText, progress: Float(state.progressPercent) / 100.0)
        latencyRow.update(state.latencyText)
        downloadRow.update(state.downloadText)
        uploadRow.update(state.uploadText)
        syncRow.update(state.uploadedText)
        statusCard.update(state.summaryText, tone: tone(for: state))

        // Same reason as the FCC card: there is no sync story until the run
        // finishes, and "Sync: Pending" on its own never said when anything
        // last reached the server, or whether uploads were failing.
        syncCard.isHidden = state.syncDetailText.isEmpty
        syncCard.update(state.syncDetailText, tone: .neutral)

        // Hidden mid-run: whether a measurement reaches the FCC is not known
        // until it finishes, and guessing early would be worse than silence.
        fccCard.isHidden = state.fccOutcomeText.isEmpty
        fccCard.update(
            state.fccOutcomeText,
            tone: state.fccOutcomeText == FccSubmissionOutcomeMessage.shared.SUBMITTED ? .success : .warning
        )

        cancelButton.isHidden = state.showCompletionActions
        doneButton.isHidden = !state.showCompletionActions
        againButton.isHidden = !state.showCompletionActions

        if state.showCompletionActions {
            UIApplication.shared.isIdleTimerDisabled = false
        }
    }

    private func tone(for state: MeasurementRunUiState) -> Components.StatusTone {
        // A run the user stopped is not a failure, so it is not coloured like one.
        if state.isCancellation { return .neutral }
        if state.isError { return .warning }
        if state.progress == MeasurementRunProgress.end { return .success }
        return .neutral
    }

    @objc private func stopTapped() { viewModel.cancel() }
    @objc private func doneTapped() { onDone() }
    @objc private func againTapped() { onMeasureAgain() }
}
