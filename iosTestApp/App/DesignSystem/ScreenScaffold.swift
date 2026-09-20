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
final class ScreenScaffold: UIView, UIGestureRecognizerDelegate {

    private let contentStack = UIStackView()
    private let actionStack = UIStackView()
    private let scrollView = UIScrollView()
    private var scrollBottomWithoutActions: NSLayoutConstraint?
    private var actionBottom: NSLayoutConstraint?

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
        // The action area is installed lazily, in addActions. An EMPTY
        // UIStackView has no intrinsic content size, so with it always present
        // the layout was under-constrained - nothing forced a height on either
        // view - and on an actionless screen the solver handed the empty stack
        // the full 818pt and crushed the scroll view to zero. That rendered a
        // completely blank screen; screens with buttons hid it, because their
        // buttons gave the stack a height. Content-hugging does not fix it:
        // hugging needs an intrinsic size to hug.

        scrollView.translatesAutoresizingMaskIntoConstraints = false
        scrollView.alwaysBounceVertical = true
        scrollView.keyboardDismissMode = .interactive
        // Tap anywhere to dismiss. Interactive drag alone is not discoverable,
        // and on the onboarding form it was the only way out of the keyboard.
        let dismissTap = UITapGestureRecognizer(target: self, action: #selector(dismissKeyboard))
        dismissTap.cancelsTouchesInView = false
        dismissTap.delegate = self
        addGestureRecognizer(dismissTap)
        observeKeyboard()
        // Inset for the navigation bar rather than sliding content beneath it;
        // the first field was being sliced by the translucent bar.
        scrollView.contentInsetAdjustmentBehavior = .always
        scrollView.addSubview(contentStack)

        addSubview(scrollView)

        scrollBottomWithoutActions = scrollView.bottomAnchor.constraint(equalTo: safeAreaLayoutGuide.bottomAnchor)
        scrollBottomWithoutActions?.isActive = true

        NSLayoutConstraint.activate([
            scrollView.topAnchor.constraint(equalTo: topAnchor),
            scrollView.leadingAnchor.constraint(equalTo: leadingAnchor),
            scrollView.trailingAnchor.constraint(equalTo: trailingAnchor),

            contentStack.topAnchor.constraint(equalTo: scrollView.contentLayoutGuide.topAnchor, constant: Theme.Space.l),
            contentStack.bottomAnchor.constraint(equalTo: scrollView.contentLayoutGuide.bottomAnchor, constant: -Theme.Space.l),
            contentStack.leadingAnchor.constraint(equalTo: scrollView.frameLayoutGuide.leadingAnchor, constant: Theme.Space.l),
            contentStack.trailingAnchor.constraint(equalTo: scrollView.frameLayoutGuide.trailingAnchor, constant: -Theme.Space.l),
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
        installActionAreaIfNeeded()
        views.forEach { actionStack.addArrangedSubview($0) }
    }

    private func installActionAreaIfNeeded() {
        guard actionStack.superview == nil else { return }
        addSubview(actionStack)
        scrollBottomWithoutActions?.isActive = false
        let bottom = actionStack.bottomAnchor.constraint(equalTo: safeAreaLayoutGuide.bottomAnchor)
        actionBottom = bottom
        NSLayoutConstraint.activate([
            actionStack.leadingAnchor.constraint(equalTo: leadingAnchor),
            actionStack.trailingAnchor.constraint(equalTo: trailingAnchor),
            bottom,
            scrollView.bottomAnchor.constraint(equalTo: actionStack.topAnchor),
        ])
    }

    /// Lifts the pinned actions clear of the keyboard.
    ///
    /// The action area is pinned to the bottom, so the keyboard covered it
    /// outright: on the onboarding form the Save button sat underneath the
    /// keyboard raised by the field above it, and the form could be completed
    /// but not submitted without first knowing to drag the keyboard away.
    private func observeKeyboard() {
        let center = NotificationCenter.default
        center.addObserver(
            self, selector: #selector(keyboardFrameChanged(_:)),
            name: UIResponder.keyboardWillChangeFrameNotification, object: nil
        )
        center.addObserver(
            self, selector: #selector(keyboardWillHide),
            name: UIResponder.keyboardWillHideNotification, object: nil
        )
    }

    @objc private func keyboardFrameChanged(_ note: Notification) {
        guard
            let frame = note.userInfo?[UIResponder.keyboardFrameEndUserInfoKey] as? CGRect,
            let window
        else { return }
        let overlap = bounds.maxY - convert(frame, from: window).minY
        // safeAreaInsets is already accounted for by the constraint's anchor.
        actionBottom?.constant = -max(0, overlap - safeAreaInsets.bottom)
        layoutIfNeeded()
    }

    @objc private func keyboardWillHide() {
        actionBottom?.constant = 0
        layoutIfNeeded()
    }

    @objc private func dismissKeyboard() {
        endEditing(true)
    }

    /// Ignores touches that land on a control.
    ///
    /// Without this the gesture ate the first tap on a button: with the
    /// keyboard up, tapping Save only dismissed the keyboard, and the layout
    /// shifted out from under the touch before the button could fire. The user
    /// had to tap Save twice, and the second tap was the only one that worked.
    func gestureRecognizer(
        _ gestureRecognizer: UIGestureRecognizer,
        shouldReceive touch: UITouch
    ) -> Bool {
        var view = touch.view
        while let candidate = view {
            if candidate is UIControl { return false }
            view = candidate.superview
        }
        return true
    }

    /// Extra gap between content groups, in scale units rather than raw points.
    func addSpacer(_ height: CGFloat = Theme.Space.s) {
        let spacer = UIView()
        spacer.heightAnchor.constraint(equalToConstant: height).isActive = true
        contentStack.addArrangedSubview(spacer)
    }
}
