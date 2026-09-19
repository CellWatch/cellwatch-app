import UIKit

/// The second screen template: a full-bleed map with a pinned bottom panel.
///
/// `ScreenScaffold` is the default and most screens use it, but a map is not
/// content in a scroll view - it wants the area, and squeezing it into a
/// fixed-height block would make the one screen users spend most of their time
/// on the worst screen in the app. frozenApp made the same call: its map
/// activity filled the screen with controls layered over it.
///
/// Divergence stops there. The panel is built from the same `Components` and
/// the same spacing scale as everywhere else, so only the arrangement differs.
final class MapScreenScaffold: UIView {

    /// Where the platform map view goes. Fills everything above the panel.
    let mapContainer = UIView()

    private let panel = UIStackView()

    init() {
        super.init(frame: .zero)
        backgroundColor = Theme.Color.background

        mapContainer.translatesAutoresizingMaskIntoConstraints = false
        mapContainer.backgroundColor = Theme.Color.greyExtraLight

        panel.axis = .vertical
        panel.spacing = Theme.Space.s
        panel.translatesAutoresizingMaskIntoConstraints = false
        panel.isLayoutMarginsRelativeArrangement = true
        panel.layoutMargins = UIEdgeInsets(
            top: Theme.Space.l, left: Theme.Space.l, bottom: Theme.Space.l, right: Theme.Space.l
        )
        panel.backgroundColor = Theme.Color.surface
        panel.layer.cornerRadius = Theme.Radius.card
        panel.layer.maskedCorners = [.layerMinXMinYCorner, .layerMaxXMinYCorner]

        addSubview(mapContainer)
        addSubview(panel)

        NSLayoutConstraint.activate([
            mapContainer.topAnchor.constraint(equalTo: topAnchor),
            mapContainer.leadingAnchor.constraint(equalTo: leadingAnchor),
            mapContainer.trailingAnchor.constraint(equalTo: trailingAnchor),
            // The panel overlaps the map's lower edge rather than sitting below
            // it, so the map keeps the full screen behind a rounded card.
            mapContainer.bottomAnchor.constraint(equalTo: panel.topAnchor, constant: Theme.Radius.card),

            panel.leadingAnchor.constraint(equalTo: leadingAnchor),
            panel.trailingAnchor.constraint(equalTo: trailingAnchor),
            panel.bottomAnchor.constraint(equalTo: bottomAnchor),
            // Never let the panel eat the map on a small screen.
            panel.topAnchor.constraint(greaterThanOrEqualTo: safeAreaLayoutGuide.topAnchor, constant: 200),
        ])
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("created in code") }

    /// Adds to the pinned panel, in order.
    func addToPanel(_ views: UIView...) {
        views.forEach { panel.addArrangedSubview($0) }
    }

    /// Replaces the panel contents, for state changes that change which rows show.
    func resetPanel() {
        panel.arrangedSubviews.forEach {
            panel.removeArrangedSubview($0)
            $0.removeFromSuperview()
        }
    }
}
