import UIKit

/// The one screen template: header, scrollable content, pinned actions.
///
/// Every product screen uses it, so screens differ in content rather than in
/// arrangement. The harness had no template, and its screens each solved
/// scrolling, margins and button placement differently.
///
/// Actions are pinned rather than scrolled: the primary action on a long form
/// should not require scrolling to find.
///
/// The scaffold does NOT render a title. The navigation bar owns the title and
/// the back button, and a scaffold title alongside it produced two headers and
/// ~150pt of dead space above the content. Screens set
/// `navigationItem.title`.
final class ScreenScaffold: UIView {

    private let contentStack = UIStackView()
    private let actionStack = UIStackView()
    private let scrollView = UIScrollView()

    init() {
        super.init(frame: .zero)
        backgroundColor = Theme.Color.background

        contentStack.axis = .vertical
        contentStack.spacing = Theme.Space.l
        contentStack.translatesAutoresizingMaskIntoConstraints = false

        actionStack.axis = .vertical
        actionStack.spacing = Theme.Space.s
        actionStack.translatesAutoresizingMaskIntoConstraints = false
        actionStack.isLayoutMarginsRelativeArrangement = true
        actionStack.layoutMargins = UIEdgeInsets(
            top: Theme.Space.m, left: Theme.Space.l, bottom: Theme.Space.m, right: Theme.Space.l
        )

        scrollView.translatesAutoresizingMaskIntoConstraints = false
        scrollView.alwaysBounceVertical = true
        scrollView.keyboardDismissMode = .interactive
        // Inset for the navigation bar rather than sliding content beneath it;
        // the first field was being sliced by the translucent bar.
        scrollView.contentInsetAdjustmentBehavior = .always
        scrollView.addSubview(contentStack)

        addSubview(scrollView)
        addSubview(actionStack)

        NSLayoutConstraint.activate([
            scrollView.topAnchor.constraint(equalTo: topAnchor),
            scrollView.leadingAnchor.constraint(equalTo: leadingAnchor),
            scrollView.trailingAnchor.constraint(equalTo: trailingAnchor),
            scrollView.bottomAnchor.constraint(equalTo: actionStack.topAnchor),

            contentStack.topAnchor.constraint(equalTo: scrollView.contentLayoutGuide.topAnchor, constant: Theme.Space.l),
            contentStack.bottomAnchor.constraint(equalTo: scrollView.contentLayoutGuide.bottomAnchor, constant: -Theme.Space.l),
            contentStack.leadingAnchor.constraint(equalTo: scrollView.frameLayoutGuide.leadingAnchor, constant: Theme.Space.l),
            contentStack.trailingAnchor.constraint(equalTo: scrollView.frameLayoutGuide.trailingAnchor, constant: -Theme.Space.l),

            actionStack.leadingAnchor.constraint(equalTo: leadingAnchor),
            actionStack.trailingAnchor.constraint(equalTo: trailingAnchor),
            actionStack.bottomAnchor.constraint(equalTo: safeAreaLayoutGuide.bottomAnchor),
        ])
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("ScreenScaffold is created in code") }

    /// Adds to the scrollable region, in order.
    func addContent(_ views: UIView...) {
        views.forEach { contentStack.addArrangedSubview($0) }
    }

    /// Adds to the pinned action region. Primary action first.
    func addActions(_ views: UIView...) {
        views.forEach { actionStack.addArrangedSubview($0) }
    }

    /// Extra gap between content groups, in scale units rather than raw points.
    func addSpacer(_ height: CGFloat = Theme.Space.s) {
        let spacer = UIView()
        spacer.heightAnchor.constraint(equalToConstant: height).isActive = true
        contentStack.addArrangedSubview(spacer)
    }
}
