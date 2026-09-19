import UIKit
import CoreLocation
import sharedKit

/// Pre-flight before a measurement: confirm the conditions, then start.
///
/// The shared `MeasurementStartViewModel` owns the decision. This file reads
/// what the platform can see - location permission and the current network
/// path - hands it over, and renders the answer.
final class MeasurementStartScreenViewController: UIViewController {

    private let viewModel: MeasurementStartViewModel
    /// Supplied by the shell, which owns container resolution. Hardcoding true
    /// here let the screen clear a gate it cannot actually see.
    private let hasRuntimeProfile: Bool
    private let networkPathProbe = IosMeasurementNetworkPathProbe()
    private let onReadyToRun: (Bool) -> Void

    private let inVehicleSwitch = UISwitch()
    private let statusLabel = Components.bodyText("", muted: true)
    private let startButton = Components.primaryButton("Start measurement")

    init(
        viewModel: MeasurementStartViewModel,
        hasRuntimeProfile: Bool,
        onReadyToRun: @escaping (Bool) -> Void
    ) {
        self.viewModel = viewModel
        self.hasRuntimeProfile = hasRuntimeProfile
        self.onReadyToRun = onReadyToRun
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("created in code") }

    override func loadView() {
        let scaffold = ScreenScaffold()

        let inVehicleRow = UIStackView(arrangedSubviews: [
            Components.bodyText("I am in a moving vehicle"),
            inVehicleSwitch,
        ])
        inVehicleRow.axis = .horizontal
        inVehicleRow.alignment = .center
        inVehicleRow.spacing = Theme.Space.m

        scaffold.addContent(
            Components.bodyText(
                "A measurement runs three tests and takes about half a minute. Keep the app open until it finishes.",
                muted: true
            ),
            Components.divider(),
            inVehicleRow,
            Components.divider(),
            statusLabel
        )
        scaffold.addActions(startButton)
        view = scaffold
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        title = "Start measurement"
        navigationController?.navigationBar.prefersLargeTitles = false
        inVehicleSwitch.addTarget(self, action: #selector(inVehicleChanged), for: .valueChanged)
        startButton.addTarget(self, action: #selector(startTapped), for: .touchUpInside)
        render(viewModel.currentState())
    }

    // MARK: - Events

    @objc private func inVehicleChanged() {
        render(viewModel.setInVehicle(value: inVehicleSwitch.isOn))
    }

    @objc private func startTapped() {
        let state = viewModel.onStartPressed(snapshot: observedCapabilities())
        render(state)
    }

    /// What the platform can see right now. Read at press time rather than
    /// cached: permission and network can both change while the screen is open.
    private func observedCapabilities() -> MeasurementStartCapabilitySnapshot {
        let status = CLLocationManager.authorizationStatus()
        return MeasurementStartCapabilitySnapshot(
            hasRuntimeProfile: hasRuntimeProfile,
            hasLocationPermission: (status == .authorizedAlways || status == .authorizedWhenInUse),
            networkPath: networkPathProbe.currentPath()
        )
    }

    // MARK: - Rendering

    private func render(_ state: MeasurementStartUiState) {
        inVehicleSwitch.isOn = state.inVehicle
        statusLabel.text = state.statusMessage
        statusLabel.textColor = state.statusIsError ? Theme.Color.warning : Theme.Color.textSecondary

        if state.shouldPromptConfirmation, let message = state.confirmationMessage {
            presentConfirmation(message)
            return
        }
        if state.readyToRun {
            onReadyToRun(state.inVehicle)
        }
    }

    /// The warning path - measuring off cellular, for instance. Answering it is
    /// the user's decision, so nothing proceeds until they do.
    private func presentConfirmation(_ message: String) {
        let alert = UIAlertController(title: "Before you start", message: message, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "Cancel", style: .cancel) { [weak self] _ in
            guard let self else { return }
            self.render(self.viewModel.onConfirmCancel())
        })
        // "Measure anyway" rather than "Continue": the shared status copy names
        // this action, so the two must agree.
        alert.addAction(UIAlertAction(title: "Measure anyway", style: .default) { [weak self] _ in
            guard let self else { return }
            self.render(self.viewModel.onConfirmProceed())
        })
        present(alert, animated: true)
    }
}
