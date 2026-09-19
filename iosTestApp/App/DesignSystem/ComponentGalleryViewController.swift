import UIKit

/// Every component on one screen, for reviewing the visual baseline.
///
/// Exists so layout decisions are made by looking at them together rather than
/// one screen at a time, which is how the harness ended up with no two screens
/// alike. Reached with the `-CellWatchComponentGallery` launch argument; it is
/// not part of the product navigation graph.
final class ComponentGalleryViewController: UIViewController {

    override func viewDidLoad() {
        super.viewDidLoad()
        title = "Component inventory"
        navigationController?.navigationBar.prefersLargeTitles = true
    }

    override func loadView() {
        let scaffold = ScreenScaffold()

        scaffold.addContent(
            Components.bodyText("The visual baseline. Spacing scale 4/8/12/16/24/32, palette from frozenApp."),
            Components.divider(),

            Components.bodyText("Metric rows", muted: true),
            Components.metricRow(label: "Latency", value: "45.3 ms"),
            Components.metricRow(label: "Download", value: "17.2 Mbps"),
            Components.metricRow(label: "Upload", value: "8.4 Mbps"),
            Components.divider(),

            Components.bodyText("Status cards", muted: true),
            Components.statusCard("Measurement complete. Results saved and synced.", tone: .success),
            Components.statusCard("3 measurements waiting to upload.", tone: .neutral),
            Components.statusCard("Cellular required. This measurement cannot be submitted.", tone: .warning),
            Components.divider(),

            Components.bodyText("Form fields", muted: true),
            Components.formField(placeholder: "Full name"),
            Components.formField(placeholder: "Email", keyboard: .emailAddress),
            Components.divider(),

            Components.bodyText("Progress header", muted: true),
            Components.progressHeader(title: "Measuring download", progress: 0.45),
            Components.divider(),

            Components.bodyText("List rows", muted: true),
            Components.listRow(title: "Measurement 19 Sep, 07:41", subtitle: "Synced", accessory: "3 tests"),
            Components.divider(),
            Components.listRow(title: "Measurement 18 Sep, 17:23", subtitle: "Pending sync", accessory: "3 tests"),
            Components.divider(),

            Components.bodyText("Empty state", muted: true),
            Components.emptyState(message: "No measurements yet. Take one to see it here.")
        )

        scaffold.addActions(
            Components.primaryButton("Primary action"),
            Components.secondaryButton("Secondary action")
        )

        view = scaffold
    }
}
