import UIKit
import sharedKit

/// First-run profile capture.
///
/// First product screen built to the contract: shared `OnboardingProfileViewModel`
/// owns state and validation, this file only renders and forwards events, and
/// everything visible comes from `Components`.
///
/// Divergence from frozenApp is deliberate and recorded in `UI_DELIVERY_PLAN.md`:
/// frozenApp onboarded through six fragments (welcome, read more, FCC
/// information, data use, collection mode, permissions). This captures the
/// contact details the FCC submission requires; the informational steps are an
/// open product question, and the navigation graph takes extra destinations
/// without rework.
final class OnboardingScreenViewController: UIViewController {

    private let viewModel: OnboardingProfileViewModel
    /// Carried from the consent step, where the real acknowledgement - the one
    /// naming what the carrier releases to the FCC - is now asked.
    private let acknowledged: Bool
    private let onComplete: () -> Void

    private let nameField = Components.formField(placeholder: "Full name")
    private let phoneField = Components.formField(placeholder: "Phone (###-###-####)", keyboard: .phonePad)
    private let emailField = Components.formField(placeholder: "Email", keyboard: .emailAddress)
    private let feedbackLabel = Components.bodyText("", muted: true)
    private let saveButton = Components.primaryButton("Save profile")

    init(
        viewModel: OnboardingProfileViewModel,
        acknowledged: Bool,
        onComplete: @escaping () -> Void
    ) {
        self.viewModel = viewModel
        self.acknowledged = acknowledged
        self.onComplete = onComplete
        super.init(nibName: nil, bundle: nil)
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("created in code") }

    override func loadView() {
        let scaffold = ScreenScaffold()

        // The acknowledgement is not asked here any more. It used to be a
        // single invented line that linked to nothing and disclosed nothing;
        // the consent step now asks frozenApp's actual sentence, after showing
        // what gets published.

        scaffold.addContent(
            Components.bodyText("Tell us who you are before starting measurements. These details accompany every submission.", muted: true),
            nameField,
            phoneField,
            emailField,
            feedbackLabel
        )
        scaffold.addActions(saveButton)
        view = scaffold
    }

    override func viewDidLoad() {
        super.viewDidLoad()
        title = "Your profile"
        navigationController?.navigationBar.prefersLargeTitles = true

        [nameField, phoneField, emailField].forEach {
            $0.addTarget(self, action: #selector(fieldChanged), for: .editingChanged)
        }
        saveButton.addTarget(self, action: #selector(save), for: .touchUpInside)

        render(viewModel.loadPersistedProfile())
        // Re-applied after load: loadPersistedProfile resets the state, which
        // would drop the acknowledgement carried in from consent and leave
        // Save permanently disabled.
        render(viewModel.onAcknowledgementChanged(acknowledged: acknowledged), echoingFields: false)
    }

    // MARK: - Events in, state out

    @objc private func fieldChanged(_ field: UITextField) {
        let text = field.text ?? ""
        switch field {
        case nameField: render(viewModel.onNameChanged(name: text), echoingFields: false)
        case phoneField: render(viewModel.onPhoneChanged(phone: text), echoingFields: false)
        case emailField: render(viewModel.onEmailChanged(email: text), echoingFields: false)
        default: break
        }
    }


    @objc private func save() {
        let submission = viewModel.submit()
        render(submission.state)
        if submission.success {
            onComplete()
        }
    }

    /// - Parameter echoingFields: whether to write state back into the text
    ///   fields. Suppressed while typing: the view model normalises input (it
    ///   formats phone numbers), and assigning that back mid-edit moves the
    ///   caret to the end after every keystroke.
    private func render(_ state: OnboardingProfileUiState, echoingFields: Bool = true) {
        if echoingFields {
            nameField.text = state.name
            phoneField.text = state.phone
            emailField.text = state.email
        }
        feedbackLabel.text = state.feedbackMessage
        feedbackLabel.textColor = state.feedbackIsError ? Theme.Color.warning : Theme.Color.success
    }
}
