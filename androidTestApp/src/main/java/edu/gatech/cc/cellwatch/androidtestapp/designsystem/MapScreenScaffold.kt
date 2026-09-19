package edu.gatech.cc.cellwatch.androidtestapp.designsystem

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import edu.gatech.cc.cellwatch.androidtestapp.designsystem.Theme.dp

/**
 * The second screen template: a full-bleed map with a pinned bottom panel.
 * Mirror of the iOS `MapScreenScaffold`.
 *
 * `ScreenScaffold` is the default, but a map is not content in a scroll view -
 * it wants the area, and boxing it into a fixed-height block would make the
 * screen users spend most of their time on the worst one in the app. frozenApp
 * made the same call. The panel uses the same components and spacing scale as
 * every other screen, so only the arrangement differs.
 */
class MapScreenScaffold(context: Context) : LinearLayout(context) {

    /** Where the platform map view goes. Fills everything above the panel. */
    val mapContainer = FrameLayout(context).apply {
        setBackgroundColor(Theme.Palette.GREY_EXTRA_LIGHT)
    }

    private val panel = LinearLayout(context).apply {
        orientation = VERTICAL
        setPadding(context.dp(Theme.Space.L), context.dp(Theme.Space.L), context.dp(Theme.Space.L), context.dp(Theme.Space.L))
        background = GradientDrawable().apply {
            setColor(Theme.Palette.SURFACE)
            cornerRadii = FloatArray(8).also { radii ->
                val r = context.dp(Theme.Radius.CARD).toFloat()
                // Top corners only: the panel sits over the map's lower edge.
                radii[0] = r; radii[1] = r; radii[2] = r; radii[3] = r
            }
        }
    }

    init {
        orientation = VERTICAL
        setBackgroundColor(Theme.Palette.BACKGROUND)
        addView(mapContainer, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        addView(panel, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }

    /** Adds to the pinned panel, in order. */
    fun addToPanel(vararg views: View) {
        views.forEach { view ->
            panel.addView(
                view,
                LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    if (panel.childCount > 0) topMargin = context.dp(Theme.Space.S)
                },
            )
        }
    }

    fun resetPanel() = panel.removeAllViews()
}
