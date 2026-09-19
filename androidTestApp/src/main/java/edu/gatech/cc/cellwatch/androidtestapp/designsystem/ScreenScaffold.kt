package edu.gatech.cc.cellwatch.androidtestapp.designsystem

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import edu.gatech.cc.cellwatch.androidtestapp.designsystem.Theme.dp

/**
 * The one screen template: scrollable content with pinned actions. Mirror of
 * the iOS `ScreenScaffold`.
 *
 * Every product screen uses it, so screens differ in content rather than in
 * arrangement. `MainActivity` had no template and solved scrolling, margins and
 * button placement differently on each screen.
 *
 * Like iOS, the scaffold does not render a title: the Activity's action bar
 * owns it. Rendering both produced two headers and a large dead band on iOS,
 * and the same would happen here.
 *
 * Actions are pinned because the primary action on a long form should not have
 * to be scrolled to.
 */
class ScreenScaffold(context: Context) : LinearLayout(context) {

    private val contentColumn = LinearLayout(context).apply {
        orientation = VERTICAL
        setPadding(context.dp(Theme.Space.L), context.dp(Theme.Space.L), context.dp(Theme.Space.L), context.dp(Theme.Space.L))
    }

    private val actionColumn = LinearLayout(context).apply {
        orientation = VERTICAL
        setPadding(context.dp(Theme.Space.L), context.dp(Theme.Space.M), context.dp(Theme.Space.L), context.dp(Theme.Space.M))
    }

    init {
        orientation = VERTICAL
        setBackgroundColor(Theme.Palette.BACKGROUND)

        addView(
            ScrollView(context).apply {
                isFillViewport = true
                addView(
                    contentColumn,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ),
                )
            },
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
        )
        addView(
            actionColumn,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
    }

    /** Adds to the scrollable region, in order, with scale spacing between entries. */
    fun addContent(vararg views: View) {
        views.forEach { view ->
            contentColumn.addView(
                view,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    if (contentColumn.childCount > 0) topMargin = context.dp(Theme.Space.L)
                },
            )
        }
    }

    /** Adds to the pinned action region. Primary action first. */
    fun addActions(vararg views: View) {
        views.forEach { view ->
            actionColumn.addView(
                view,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply {
                    if (actionColumn.childCount > 0) topMargin = context.dp(Theme.Space.S)
                },
            )
        }
    }
}
