package edu.gatech.cc.cellwatch.androidtestapp.designsystem

import android.content.Context
import android.graphics.Color
import android.util.TypedValue

/**
 * Visual baseline for product screens. Mirror of the iOS `Theme`.
 *
 * Both platforms carry the same palette, the same 4-based spacing scale and the
 * same template so the two apps stay recognisably one product. The palette is
 * frozenApp's `res/values/colors.xml`; the scale is what its 21 layouts
 * actually used - 16dp 58 times, 8dp 47, 4dp 17, 12dp 15, 24dp 7 - with the
 * off-scale values (2, 5, 6, 10dp) being the drift this replaces.
 *
 * Values are dp, converted at use. Nothing here is a raw pixel.
 */
object Theme {

    object Palette {
        const val BLUE = 0xFF07416B.toInt()
        const val BLUE_LIGHT = 0xFF5E8BAB.toInt()
        const val GREEN = 0xFF1E5638.toInt()
        const val GREEN_LIGHT = 0xFFC8E3CC.toInt()
        const val GREY = 0xFF777777.toInt()
        const val GREY_DARK = 0xFF464646.toInt()
        const val GREY_EXTRA_LIGHT = 0xFFDFDFDF.toInt()
        const val GREY_ULTRA_LIGHT = 0xFFF2F2F2.toInt()
        const val ORANGE = 0xFFB44D0D.toInt()

        // Roles. Screens use these, not raw hues.
        const val PRIMARY = BLUE
        const val ON_PRIMARY = Color.WHITE
        const val SUCCESS = GREEN
        const val WARNING = ORANGE
        const val SURFACE = Color.WHITE
        const val BACKGROUND = GREY_ULTRA_LIGHT
        const val BORDER = GREY_EXTRA_LIGHT
        const val TEXT_PRIMARY = 0xFF1A1A1A.toInt()
        const val TEXT_SECONDARY = GREY_DARK
        const val TEXT_MUTED = GREY
    }

    /** dp. Names, not numbers, at call sites. */
    object Space {
        const val XS = 4
        const val S = 8
        const val M = 12
        const val L = 16
        const val XL = 24
        const val XXL = 32
    }

    object Radius {
        const val CONTROL = 10
        const val CARD = 12
    }

    /** sp. Matches the iOS type ramp. */
    object TextSize {
        const val TITLE = 24f
        const val HEADING = 18f
        const val BODY = 16f
        const val CAPTION = 13f
        const val METRIC = 17f
    }

    /** Material's minimum touch target, and iOS's. */
    const val MIN_TAP_TARGET_DP = 48

    fun Context.dp(value: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value.toFloat(),
        resources.displayMetrics,
    ).toInt()
}
