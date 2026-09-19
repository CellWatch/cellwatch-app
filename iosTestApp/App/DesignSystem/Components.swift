import UIKit

/// The component inventory.
///
/// Screens compose these; they do not build `UILabel`/`UIButton` hierarchies
/// inline. The harness did the latter — 42 `UILabel`s and 34 buttons across one
/// 4,330-line file, hand-positioned against a single stack view — which is why
/// no two screens look alike.
///
/// Everything here is stack-view based on purpose: `NSLayoutConstraint` blocks
/// are what made the harness layouts unrepeatable, so spacing comes from
/// `Theme.Space` through stacks rather than per-screen constraints.
///
/// Adding a screen that needs something new means adding it here first.
enum Components {

    // MARK: - Actions

    static func primaryButton(_ title: String) -> UIButton {
        let button = filledButton(title, background: Theme.Color.primary, foreground: Theme.Color.onPrimary)
        return button
    }

    static func secondaryButton(_ title: String) -> UIButton {
        let button = filledButton(title, background: Theme.Color.surface, foreground: Theme.Color.primary)
        button.layer.borderWidth = 1
        button.layer.borderColor = Theme.Color.primary.cgColor
        return button
    }

    private static func filledButton(_ title: String, background: UIColor, foreground: UIColor) -> UIButton {
        let button = UIButton(type: .system)
        button.setTitle(title, for: .normal)
        button.setTitleColor(foreground, for: .normal)
        button.titleLabel?.font = Theme.Font.heading
        button.titleLabel?.adjustsFontForContentSizeCategory = true
        button.backgroundColor = background
        button.layer.cornerRadius = Theme.Radius.control
        button.contentEdgeInsets = UIEdgeInsets(
            top: Theme.Space.m, left: Theme.Space.l, bottom: Theme.Space.m, right: Theme.Space.l
        )
        button.heightAnchor.constraint(greaterThanOrEqualToConstant: Theme.minimumTapTarget).isActive = true
        return button
    }

    // MARK: - Text

    static func sectionHeader(_ text: String) -> UILabel {
        let label = UILabel()
        label.text = text
        label.font = Theme.Font.title
        label.textColor = Theme.Color.textPrimary
        label.numberOfLines = 0
        label.adjustsFontForContentSizeCategory = true
        return label
    }

    static func bodyText(_ text: String, muted: Bool = false) -> UILabel {
        let label = UILabel()
        label.text = text
        label.font = Theme.Font.body
        label.textColor = muted ? Theme.Color.textSecondary : Theme.Color.textPrimary
        label.numberOfLines = 0
        label.adjustsFontForContentSizeCategory = true
        return label
    }

    /// Label on the left, value on the right. The measurement results and
    /// history screens are mostly these.
    static func metricRow(label: String, value: String) -> UIView {
        let row = MetricRowView(label: label)
        row.update(value)
        return row
    }

    /// Stateful ``metricRow``: latency, download and upload each land at a
    /// different point in a run, so the values are filled in as they arrive.
    final class MetricRowView: UIStackView {
        private let reading = UILabel()

        init(label: String) {
            let name = UILabel()
            name.text = label
            name.font = Theme.Font.body
            name.textColor = Theme.Color.textSecondary
            name.adjustsFontForContentSizeCategory = true

            reading.font = Theme.Font.metric
            reading.textColor = Theme.Color.textPrimary
            reading.textAlignment = .right
            reading.adjustsFontForContentSizeCategory = true
            // The value must never be truncated in favour of its label: a
            // clipped number is worse than a wrapped word.
            reading.setContentCompressionResistancePriority(.required, for: .horizontal)
            reading.setContentHuggingPriority(.required, for: .horizontal)

            super.init(frame: .zero)
            addArrangedSubview(name)
            addArrangedSubview(reading)
            axis = .horizontal
            alignment = .firstBaseline
            spacing = Theme.Space.m
        }

        required init(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

        func update(_ value: String) {
            reading.text = value
        }
    }

    enum StatusTone {
        case neutral, success, warning

        var accent: UIColor {
            switch self {
            case .neutral: return Theme.Color.blueLight
            case .success: return Theme.Color.green
            case .warning: return Theme.Color.orange
            }
        }
    }

    /// Boxed message. Used for sync state, validation results, empty warnings.
    static func statusCard(_ text: String, tone: StatusTone = .neutral) -> UIView {
        let card = StatusCardView()
        card.update(text, tone: tone)
        return card
    }

    /// Stateful ``statusCard``. A measurement run rewrites both its message and
    /// its tone as the run progresses, so it has to be updatable in place.
    final class StatusCardView: UIView {
        private let accent = UIView()
        private let label = bodyText("")

        init() {
            super.init(frame: .zero)
            backgroundColor = Theme.Color.surface
            layer.cornerRadius = Theme.Radius.card
            layer.borderWidth = 1
            layer.borderColor = Theme.Color.border.cgColor

            accent.layer.cornerRadius = 2
            accent.translatesAutoresizingMaskIntoConstraints = false
            accent.widthAnchor.constraint(equalToConstant: Theme.Space.xs).isActive = true

            let row = UIStackView(arrangedSubviews: [accent, label])
            row.axis = .horizontal
            row.alignment = .fill
            row.spacing = Theme.Space.m
            row.translatesAutoresizingMaskIntoConstraints = false
            row.isLayoutMarginsRelativeArrangement = true
            row.layoutMargins = UIEdgeInsets(
                top: Theme.Space.m, left: Theme.Space.m, bottom: Theme.Space.m, right: Theme.Space.m
            )

            addSubview(row)
            NSLayoutConstraint.activate([
                row.topAnchor.constraint(equalTo: topAnchor),
                row.bottomAnchor.constraint(equalTo: bottomAnchor),
                row.leadingAnchor.constraint(equalTo: leadingAnchor),
                row.trailingAnchor.constraint(equalTo: trailingAnchor),
            ])
        }

        required init(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

        func update(_ text: String, tone: StatusTone) {
            label.text = text
            accent.backgroundColor = tone.accent
        }
    }

    // MARK: - Input

    static func formField(placeholder: String, keyboard: UIKeyboardType = .default) -> UITextField {
        let field = UITextField()
        field.placeholder = placeholder
        field.font = Theme.Font.body
        field.adjustsFontForContentSizeCategory = true
        field.keyboardType = keyboard
        // Capitalisation follows the keyboard, because the default does the
        // wrong thing: typing an address into an email field produced
        // "Jw199@gatech.edu" on the first run of the onboarding screen.
        switch keyboard {
        case .emailAddress:
            field.autocapitalizationType = .none
            field.autocorrectionType = .no
            field.textContentType = .emailAddress
        case .phonePad, .numberPad:
            field.autocapitalizationType = .none
            field.autocorrectionType = .no
            field.textContentType = .telephoneNumber
        default:
            field.autocapitalizationType = .words
            field.textContentType = .name
        }
        field.borderStyle = .none
        field.backgroundColor = Theme.Color.surface
        field.layer.cornerRadius = Theme.Radius.control
        field.layer.borderWidth = 1
        field.layer.borderColor = Theme.Color.border.cgColor
        field.leftView = UIView(frame: CGRect(x: 0, y: 0, width: Theme.Space.m, height: 1))
        field.leftViewMode = .always
        field.heightAnchor.constraint(greaterThanOrEqualToConstant: Theme.minimumTapTarget).isActive = true
        return field
    }

    // MARK: - State

    /// Title plus progress bar, for a running measurement.
    static func progressHeader(title: String, progress: Float) -> UIView {
        let header = ProgressHeaderView()
        header.update(title, progress: progress)
        return header
    }

    /// Stateful ``progressHeader``: a run updates title and bar on every stage.
    final class ProgressHeaderView: UIStackView {
        private let heading = sectionHeader("")
        private let bar = UIProgressView(progressViewStyle: .default)

        init() {
            super.init(frame: .zero)
            bar.progressTintColor = Theme.Color.primary
            bar.trackTintColor = Theme.Color.greyExtraLight
            addArrangedSubview(heading)
            addArrangedSubview(bar)
            axis = .vertical
            spacing = Theme.Space.s
        }

        required init(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

        func update(_ title: String, progress: Float) {
            heading.text = title
            bar.setProgress(progress, animated: false)
        }
    }

    /// Shown instead of an empty list. An empty screen with no explanation
    /// reads as a bug.
    static func emptyState(message: String) -> UIView {
        let label = bodyText(message, muted: true)
        label.textAlignment = .center

        let stack = UIStackView(arrangedSubviews: [label])
        stack.axis = .vertical
        stack.isLayoutMarginsRelativeArrangement = true
        stack.layoutMargins = UIEdgeInsets(
            top: Theme.Space.xl, left: Theme.Space.l, bottom: Theme.Space.xl, right: Theme.Space.l
        )
        return stack
    }

    /// One tappable entry in a list — history entries, settings rows.
    static func listRow(title: String, subtitle: String?, accessory: String? = nil) -> UIView {
        let titleLabel = bodyText(title)
        let textStack = UIStackView(arrangedSubviews: [titleLabel])
        textStack.axis = .vertical
        textStack.spacing = Theme.Space.xs
        if let subtitle {
            let sub = UILabel()
            sub.text = subtitle
            sub.font = Theme.Font.caption
            sub.textColor = Theme.Color.textMuted
            sub.numberOfLines = 0
            sub.adjustsFontForContentSizeCategory = true
            textStack.addArrangedSubview(sub)
        }

        let row = UIStackView(arrangedSubviews: [textStack])
        if let accessory {
            let badge = UILabel()
            badge.text = accessory
            badge.font = Theme.Font.caption
            badge.textColor = Theme.Color.textSecondary
            badge.setContentHuggingPriority(.required, for: .horizontal)
            row.addArrangedSubview(badge)
        }
        row.axis = .horizontal
        row.alignment = .center
        row.spacing = Theme.Space.m
        row.isLayoutMarginsRelativeArrangement = true
        row.layoutMargins = UIEdgeInsets(
            top: Theme.Space.m, left: 0, bottom: Theme.Space.m, right: 0
        )
        row.heightAnchor.constraint(greaterThanOrEqualToConstant: Theme.minimumTapTarget).isActive = true
        return row
    }

    static func divider() -> UIView {
        let line = UIView()
        line.backgroundColor = Theme.Color.border
        line.heightAnchor.constraint(equalToConstant: 1).isActive = true
        return line
    }
}
