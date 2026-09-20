import UIKit
import sharedKit

/// Write the stored measurements to a file. Mirror of `ExportScreen` on
/// Android.
///
/// iOS has no Storage Access Framework, so the document is written to a
/// temporary file and handed to the share sheet, which is the platform's
/// equivalent of "choose where this goes".
final class ExportScreenViewController: UIViewController {

    private let buildFcc: (@escaping (ExportDocument) -> Void) -> Void
    private let buildExtended: (@escaping (ExportDocument) -> Void) -> Void
    private let onBack: () -> Void

    private let scaffold = ScreenScaffold()
    private let status = Components.StatusCardView()
    private lazy var fccButton = Components.primaryButton(ExportCopy.shared.EXPORT_FCC_FILE)
    private lazy var extendedButton = Components.secondaryButton(ExportCopy.shared.EXPORT_FULL_DATA)
    private lazy var backButton = Components.secondaryButton(MapHomeCopy.shared.BACK_TO_MAP)

    init(
        buildFcc: @escaping (@escaping (ExportDocument) -> Void) -> Void,
        buildExtended: @escaping (@escaping (ExportDocument) -> Void) -> Void,
        onBack: @escaping () -> Void
    ) {
        self.buildFcc = buildFcc
        self.buildExtended = buildExtended
        self.onBack = onBack
        super.init(nibName: nil, bundle: nil)
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    override func loadView() { view = scaffold }

    override func viewDidLoad() {
        super.viewDidLoad()
        fccButton.addTarget(self, action: #selector(fccTapped), for: .touchUpInside)
        extendedButton.addTarget(self, action: #selector(extendedTapped), for: .touchUpInside)
        backButton.addTarget(self, action: #selector(backTapped), for: .touchUpInside)

        scaffold.addContent(
            Components.sectionHeader(ExportCopy.shared.FCC_FILE_TITLE),
            Components.bodyText(
                "The format the FCC accepts for a challenge submission. It contains only "
                    + "measurements that qualified: taken over cellular, complete, and with "
                    + "submission turned on. If none qualified, this file will be empty.",
                muted: true
            ),
            Components.divider(),
            Components.sectionHeader(ExportCopy.shared.FULL_EXPORT_TITLE),
            Components.bodyText(
                "Everything this device recorded, including measurements the FCC file leaves "
                    + "out and the reason each one was left out. Also records what the device "
                    + "could not report — missing permissions, unavailable telephony — which "
                    + "the FCC format has no field for.",
                muted: true
            ),
            status
        )
        scaffold.addActions(fccButton, extendedButton, backButton)
        status.update(ExportCopy.shared.CHOOSE_FORMAT, tone: .neutral)
    }

    @objc private func fccTapped() {
        status.update(ExportCopy.shared.PREPARING_FCC_FILE, tone: .neutral)
        buildFcc { [weak self] document in self?.share(document, label: ExportCopy.shared.FCC_FILE_TITLE) }
    }

    @objc private func extendedTapped() {
        status.update(ExportCopy.shared.PREPARING_FULL_EXPORT, tone: .neutral)
        buildExtended { [weak self] document in self?.share(document, label: ExportCopy.shared.FULL_EXPORT_TITLE) }
    }

    @objc private func backTapped() { onBack() }

    private func share(_ document: ExportDocument, label: String) {
        // An empty FCC file is a legitimate outcome, not an error, but saying
        // so up front is kinder than handing over a file with nothing in it.
        status.update(
            ExportCopy.shared.ready(label: label, recordCount: document.recordCount),
            tone: document.recordCount == 0 ? .warning : .success
        )

        let url = FileManager.default.temporaryDirectory.appendingPathComponent(document.fileName)
        do {
            try document.json.write(to: url, atomically: true, encoding: .utf8)
        } catch {
            status.update(ExportCopy.shared.notSaved(reason: error.localizedDescription), tone: .warning)
            return
        }

        let share = UIActivityViewController(activityItems: [url], applicationActivities: nil)
        // Required on iPad, where a share sheet without an anchor traps.
        share.popoverPresentationController?.sourceView = fccButton
        share.popoverPresentationController?.sourceRect = fccButton.bounds
        present(share, animated: true)
    }
}
