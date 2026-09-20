import UIKit

/// A screen whose middle is a list and whose ends are not.
///
/// `ScreenScaffold` scrolls everything together, which is right for a form and
/// wrong for a list: on History it put the selected run's detail *below* the
/// run list, so choosing a run scrolled its own detail off the bottom, and the
/// more history a user had the further away the answer moved. Here the header
/// and the detail stay put and only the rows move.
///
/// Mirror of the Android `ListScreenScaffold`.
final class ListScreenScaffold: UIView {

    private let headerStack = UIStackView()
    private let listStack = UIStackView()
    private let actionStack = UIStackView()
    private let scrollView = UIScrollView()

    init() {
        super.init(frame: .zero)
        backgroundColor = Theme.Color.background

        [headerStack, listStack, actionStack].forEach {
            $0.axis = .vertical
            $0.translatesAutoresizingMaskIntoConstraints = false
        }
        headerStack.spacing = Theme.Space.m
        listStack.spacing = Theme.Space.s
        actionStack.spacing = Theme.Space.s

        headerStack.isLayoutMarginsRelativeArrangement = true
        headerStack.layoutMargins = UIEdgeInsets(
            top: Theme.Space.l, left: Theme.Space.l, bottom: Theme.Space.m, right: Theme.Space.l
        )
        actionStack.isLayoutMarginsRelativeArrangement = true
        actionStack.layoutMargins = UIEdgeInsets(
            top: Theme.Space.m, left: Theme.Space.l, bottom: Theme.Space.m, right: Theme.Space.l
        )

        scrollView.translatesAutoresizingMaskIntoConstraints = false
        scrollView.alwaysBounceVertical = true
        scrollView.addSubview(listStack)

        addSubview(headerStack)
        addSubview(scrollView)
        addSubview(actionStack)

        NSLayoutConstraint.activate([
            headerStack.topAnchor.constraint(equalTo: safeAreaLayoutGuide.topAnchor),
            headerStack.leadingAnchor.constraint(equalTo: leadingAnchor),
            headerStack.trailingAnchor.constraint(equalTo: trailingAnchor),

            scrollView.topAnchor.constraint(equalTo: headerStack.bottomAnchor),
            scrollView.leadingAnchor.constraint(equalTo: leadingAnchor),
            scrollView.trailingAnchor.constraint(equalTo: trailingAnchor),
            scrollView.bottomAnchor.constraint(equalTo: actionStack.topAnchor),
            // Without a floor, a tall header plus a long selected-run detail
            // can squeeze the list to nothing on a short screen - and a list
            // of zero height reads as "no history" rather than "scroll me".
            scrollView.heightAnchor.constraint(greaterThanOrEqualToConstant: 140),

            listStack.topAnchor.constraint(equalTo: scrollView.contentLayoutGuide.topAnchor, constant: Theme.Space.s),
            listStack.bottomAnchor.constraint(equalTo: scrollView.contentLayoutGuide.bottomAnchor, constant: -Theme.Space.s),
            listStack.leadingAnchor.constraint(equalTo: scrollView.frameLayoutGuide.leadingAnchor, constant: Theme.Space.l),
            listStack.trailingAnchor.constraint(equalTo: scrollView.frameLayoutGuide.trailingAnchor, constant: -Theme.Space.l),

            actionStack.leadingAnchor.constraint(equalTo: leadingAnchor),
            actionStack.trailingAnchor.constraint(equalTo: trailingAnchor),
            actionStack.bottomAnchor.constraint(equalTo: safeAreaLayoutGuide.bottomAnchor),
        ])
    }

    @available(*, unavailable)
    required init?(coder: NSCoder) { fatalError("created in code") }

    /// Fixed region above the list.
    func addHeader(_ views: UIView...) {
        views.forEach { headerStack.addArrangedSubview($0) }
    }

    /// Replaces the scrolling region's contents.
    func setListItems(_ views: [UIView]) {
        listStack.arrangedSubviews.forEach { $0.removeFromSuperview() }
        views.forEach { listStack.addArrangedSubview($0) }
        scrollView.setContentOffset(.zero, animated: false)
    }

    /// Pinned below the list. Primary action first.
    func addActions(_ views: UIView...) {
        views.forEach { actionStack.addArrangedSubview($0) }
    }
}
