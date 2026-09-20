import UIKit
import sharedKit

/// What happens to the data, shown before anything is collected.
/// Copy is frozenApp's, verbatim, via the shared `ConsentCopy`.
final class DataUseScreenViewController: UIViewController {

    private let onContinue: () -> Void
    private let scaffold = ScreenScaffold()
    private lazy var continueButton = Components.primaryButton(ConsentCopy.shared.CONTINUE)
    private lazy var policyButton = Components.secondaryButton(ConsentCopy.shared.PRIVACY_POLICY_LABEL)

    init(onContinue: @escaping () -> Void) {
        self.onContinue = onContinue
        super.init(nibName: nil, bundle: nil)
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    override func loadView() { view = scaffold }

    override func viewDidLoad() {
        super.viewDidLoad()
        continueButton.addTarget(self, action: #selector(continueTapped), for: .touchUpInside)
        policyButton.addTarget(self, action: #selector(policyTapped), for: .touchUpInside)

        scaffold.addContent(
            Components.sectionHeader(ConsentCopy.shared.DATA_USE_TITLE),
            Components.bodyText(ConsentCopy.shared.DATA_USE_SHARED),
            Components.bodyText(ConsentCopy.shared.DATA_USE_RESEARCH),
            Components.bodyText(ConsentCopy.shared.DATA_USE_POLICY, muted: true),
            // The address is shown, not just linked: a user agreeing to a
            // published policy should be able to see where it lives.
            Components.bodyText(ConsentCopy.shared.PRIVACY_POLICY_URL, muted: true)
        )
        scaffold.addActions(continueButton, policyButton)
    }

    @objc private func continueTapped() { onContinue() }

    @objc private func policyTapped() {
        guard let url = URL(string: ConsentCopy.shared.PRIVACY_POLICY_URL) else { return }
        UIApplication.shared.open(url)
    }
}
