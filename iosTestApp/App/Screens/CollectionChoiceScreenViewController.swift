import UIKit
import sharedKit

/// Collection mode and the FCC acknowledgement. Copy is frozenApp's, verbatim.
final class CollectionChoiceScreenViewController: UIViewController {

    private let viewModel: ConsentViewModel
    private let onContinue: (CollectionMode, Bool) -> Void

    private let scaffold = ScreenScaffold()
    private let challengeSwitch = UISwitch()
    private let acknowledgeSwitch = UISwitch()
    private let modeDetail = Components.bodyText("", muted: true)
    private let fccHeader = Components.sectionHeader(ConsentCopy.shared.FCC_INFO_TITLE)
    private let fccInfo = Components.bodyText(ConsentCopy.shared.FCC_INFO_DESCRIPTION, muted: true)
    private let status = Components.bodyText("", muted: true)
    private lazy var continueButton = Components.primaryButton("Continue")
    private lazy var acknowledgeRow = switchRow(ConsentCopy.shared.FCC_ACKNOWLEDGEMENT, acknowledgeSwitch)

    private var rendering = false

    init(viewModel: ConsentViewModel, onContinue: @escaping (CollectionMode, Bool) -> Void) {
        self.viewModel = viewModel
        self.onContinue = onContinue
        super.init(nibName: nil, bundle: nil)
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    override func loadView() { view = scaffold }

    override func viewDidLoad() {
        super.viewDidLoad()
        challengeSwitch.addTarget(self, action: #selector(switchChanged(_:)), for: .valueChanged)
        acknowledgeSwitch.addTarget(self, action: #selector(switchChanged(_:)), for: .valueChanged)
        continueButton.addTarget(self, action: #selector(continueTapped), for: .touchUpInside)

        scaffold.addContent(
            Components.sectionHeader(ConsentCopy.shared.COLLECTION_MODE_TITLE),
            Components.bodyText(ConsentCopy.shared.COLLECTION_MODE_DESCRIPTION),
            Components.divider(),
            switchRow(ConsentCopy.shared.CHALLENGE_MODE_TITLE, challengeSwitch),
            modeDetail,
            Components.divider(),
            fccHeader,
            fccInfo,
            acknowledgeRow,
            status
        )
        scaffold.addActions(continueButton)
        render(viewModel.currentState())
    }

    private func switchRow(_ label: String, _ control: UISwitch) -> UIView {
        let caption = Components.bodyText(label)
        // The switch keeps its width and the caption wraps; these captions are
        // full sentences and would otherwise push the control off-screen.
        caption.setContentCompressionResistancePriority(.defaultLow, for: .horizontal)
        control.setContentCompressionResistancePriority(.required, for: .horizontal)
        control.setContentHuggingPriority(.required, for: .horizontal)
        let row = UIStackView(arrangedSubviews: [caption, control])
        row.axis = .horizontal
        row.alignment = .center
        row.spacing = Theme.Space.m
        return row
    }

    @objc private func switchChanged(_ sender: UISwitch) {
        guard !rendering else { return }
        if sender === challengeSwitch {
            render(viewModel.onCollectionModeChanged(
                mode: sender.isOn ? CollectionMode.fccChallenge : CollectionMode.testing
            ))
        } else {
            render(viewModel.onAcknowledgementChanged(value: sender.isOn))
        }
    }

    @objc private func continueTapped() {
        let state = viewModel.currentState()
        guard state.canContinue else { return }
        onContinue(state.collectionMode, state.acknowledged)
    }

    private func render(_ state: ConsentUiState) {
        rendering = true
        challengeSwitch.isOn = state.challengeSelected
        acknowledgeSwitch.isOn = state.acknowledged
        modeDetail.text = state.challengeSelected
            ? ConsentCopy.shared.CHALLENGE_MODE_DESCRIPTION
            : ConsentCopy.shared.TESTING_MODE_DESCRIPTION
        // The FCC section is about what is sent to the FCC; in testing mode
        // nothing is, so asking would be about something that will not happen.
        let fccRelevant = state.challengeSelected
        fccHeader.isHidden = !fccRelevant
        fccInfo.isHidden = !fccRelevant
        acknowledgeRow.isHidden = !fccRelevant
        status.text = state.statusMessage
        continueButton.isEnabled = state.canContinue
        continueButton.alpha = state.canContinue ? 1.0 : 0.5
        rendering = false
    }
}
