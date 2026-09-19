import UIKit

/// Visual baseline for product screens.
///
/// Derived from `frozenApp`, not invented: the palette is its `colors.xml`, and
/// the spacing scale is what its 21 layouts actually used — 16dp appears 58
/// times, 8dp 47, 4dp 17, 12dp 15, 24dp 7. Values outside that scale (2, 5, 6,
/// 10dp) are the ad-hoc drift this replaces; frozenApp's own `dimens.xml` was
/// empty, so spacing was decided per layout.
///
/// Screens use these tokens. A screen that needs a value not here changes the
/// scale deliberately rather than hard-coding a one-off.
enum Theme {

    /// CellWatch palette, from frozenApp `res/values/colors.xml`.
    enum Color {
        static let blue = UIColor(hex: 0x07416B)
        static let blueLight = UIColor(hex: 0x5E8BAB)
        static let green = UIColor(hex: 0x1E5638)
        static let greenLight = UIColor(hex: 0xC8E3CC)
        static let greenDark = UIColor(hex: 0x003618)
        static let grey = UIColor(hex: 0x777777)
        static let greyDark = UIColor(hex: 0x464646)
        static let greyLight = UIColor(hex: 0xBABABA)
        static let greyExtraLight = UIColor(hex: 0xDFDFDF)
        static let greyUltraLight = UIColor(hex: 0xF2F2F2)
        static let orange = UIColor(hex: 0xB44D0D)

        // Roles. Screens reference these, not the raw palette, so a palette
        // change lands in one place.
        static let primary = blue
        static let onPrimary = UIColor.white
        static let success = green
        static let warning = orange
        static let surface = UIColor.white
        static let background = greyUltraLight
        static let border = greyExtraLight
        static let textPrimary = UIColor(hex: 0x1A1A1A)
        static let textSecondary = greyDark
        static let textMuted = grey
    }

    /// 4-based scale. Names, not numbers, at call sites.
    enum Space {
        static let xs: CGFloat = 4
        static let s: CGFloat = 8
        static let m: CGFloat = 12
        static let l: CGFloat = 16
        static let xl: CGFloat = 24
        static let xxl: CGFloat = 32
    }

    enum Radius {
        static let control: CGFloat = 10
        static let card: CGFloat = 12
    }

    /// Dynamic Type throughout: these scale with the user's text size setting,
    /// which fixed-point fonts would not.
    enum Font {
        static let title = UIFont.preferredFont(forTextStyle: .title2).semibold()
        static let heading = UIFont.preferredFont(forTextStyle: .headline)
        static let body = UIFont.preferredFont(forTextStyle: .body)
        static let caption = UIFont.preferredFont(forTextStyle: .footnote)
        static let metric = UIFont.monospacedDigitSystemFont(ofSize: 17, weight: .semibold)
    }

    /// Minimum tap target. Apple's guidance is 44pt; several existing harness
    /// buttons are smaller.
    static let minimumTapTarget: CGFloat = 44
}

extension UIColor {
    convenience init(hex: UInt32) {
        self.init(
            red: CGFloat((hex >> 16) & 0xFF) / 255.0,
            green: CGFloat((hex >> 8) & 0xFF) / 255.0,
            blue: CGFloat(hex & 0xFF) / 255.0,
            alpha: 1.0
        )
    }
}

extension UIFont {
    func semibold() -> UIFont {
        guard let descriptor = fontDescriptor.withSymbolicTraits(.traitBold) else { return self }
        return UIFont(descriptor: descriptor, size: 0)
    }
}
