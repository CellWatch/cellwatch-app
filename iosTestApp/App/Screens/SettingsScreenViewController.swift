import UIKit
import sharedKit

/// Edit the profile, choose the collection mode, and see what the app is
/// talking to. Mirror of `SettingsScreen` on Android, on the same shared
/// `SettingsProfileViewModel` the harness already uses.
final class SettingsScreenViewController: UIViewController {

    private let viewModel: SettingsProfileViewModel
    private let diagnosticsProvider: (@escaping (ProductDiagnostics) -> Void) -> Void
    private let onSaved: () -> Void
    private let onBack: () -> Void

    private let scaffold = ScreenScaffold()
    private let nameField = Components.formField(placeholder: "Full name")
    private let phoneField = Components.formField(placeholder: "Phone (###-###-####)", keyboard: .phonePad)
    private let emailField = Components.formField(placeholder: "Email", keyboard: .emailAddress)
    private let acknowledgeSwitch = UISwitch()
    private let challengeSwitch = UISwitch()
    private let feedback = Components.bodyText("", muted: true)
    private let diagnostics = Components.bodyText("Loading…", muted: true)
    private lazy var saveButton = Components.primaryButton("Save settings")
    private lazy var backButton = Components.secondaryButton("Back to map")

    /// Guards the editing callbacks while render writes values back.
    private var rendering = false

    init(
        viewModel: SettingsProfileViewModel,
        diagnosticsProvider: @escaping (@escaping (ProductDiagnostics) -> Void) -> Void,
        onSaved: @escaping () -> Void,
        onBack: @escaping () -> Void
    ) {
        self.viewModel = viewModel
        self.diagnosticsProvider = diagnosticsProvider
        self.onSaved = onSaved
        self.onBack = onBack
        super.init(nibName: nil, bundle: nil)
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    override func loadView() { view = scaffold }

    override func viewDidLoad() {
        super.viewDidLoad()

        [nameField, phoneField, emailField].forEach {
            $0.addTarget(self, action: #selector(fieldChanged(_:)), for: .editingChanged)
        }
        acknowledgeSwitch.addTarget(self, action: #selector(switchChanged), for: .valueChanged)
        challengeSwitch.addTarget(self, action: #selector(switchChanged), for: .valueChanged)
        saveButton.addTarget(self, action: #selector(saveTapped), for: .touchUpInside)
        backButton.addTarget(self, action: #selector(backTapped), for: .touchUpInside)

        scaffold.addContent(
            Components.bodyText(
                "These details accompany every submission. Changing them affects future "
                    + "measurements, not ones already uploaded.",
                muted: true
            ),
            nameField,
            phoneField,
            emailField,
            switchRow("I acknowledge the FCC challenge sharing terms.", acknowledgeSwitch),
            Components.divider(),
            switchRow("Submit measurements to the FCC challenge", challengeSwitch),
            Components.bodyText(
                "With this off, measurements are still taken and saved, but no FCC submission "
                    + "is created for them.",
                muted: true
            ),
            feedback,
            Components.divider(),
            Components.sectionHeader("About this install"),
            diagnostics
        )
        scaffold.addActions(saveButton, backButton)

        render(viewModel.loadPersistedProfile())
        diagnosticsProvider { [weak self] value in self?.renderDiagnostics(value) }
    }

    private func switchRow(_ label: String, _ control: UISwitch) -> UIView {
        let caption = Components.bodyText(label)
        // The switch keeps its width and the caption wraps. Without this the
        // longer caption pushed the switch off the trailing edge, where it
        // could be neither seen nor tapped.
        caption.setContentCompressionResistancePriority(.defaultLow, for: .horizontal)
        control.setContentCompressionResistancePriority(.required, for: .horizontal)
        control.setContentHuggingPriority(.required, for: .horizontal)

        let row = UIStackView(arrangedSubviews: [caption, control])
        row.axis = .horizontal
        row.alignment = .center
        row.spacing = Theme.Space.m
        return row
    }

    @objc private func fieldChanged(_ sender: UITextField) {
        guard !rendering else { return }
        let text = sender.text ?? ""
        switch sender {
        case nameField: render(viewModel.onNameChanged(name: text))
        case phoneField: render(viewModel.onPhoneChanged(phone: text))
        default: render(viewModel.onEmailChanged(email: text))
        }
    }

    @objc private func switchChanged(_ sender: UISwitch) {
        guard !rendering else { return }
        if sender === acknowledgeSwitch {
            render(viewModel.onAcknowledgementChanged(acknowledged: sender.isOn))
        } else {
            render(viewModel.onCollectionModeChanged(
                collectionMode: sender.isOn ? CollectionMode.fccChallenge : CollectionMode.testing
            ))
        }
    }

    @objc private func saveTapped() {
        let submission = viewModel.submit()
        render(submission.state)
        if submission.success { onSaved() }
    }

    @objc private func backTapped() { onBack() }

    private func render(_ state: SettingsProfileUiState) {
        rendering = true
        if nameField.text != state.name { nameField.text = state.name }
        if phoneField.text != state.phone { phoneField.text = state.phone }
        if emailField.text != state.email { emailField.text = state.email }
        acknowledgeSwitch.isOn = state.fccAcknowledged
        challengeSwitch.isOn = state.collectionMode == CollectionMode.fccChallenge
        feedback.text = state.feedbackMessage
        feedback.textColor = state.feedbackIsError ? Theme.Color.warning : Theme.Color.textSecondary
        rendering = false
    }

    private func renderDiagnostics(_ d: ProductDiagnostics) {
        diagnostics.text = """
            \(d.appName) \(d.appVersion)
            Device ID: \(d.deviceId)
            Measurement server: \(d.msakMode) (\(d.msakEndpoint))
            Upload target: \(d.supabaseMode)
            """
    }
}
