package edu.gatech.cc.cellwatch.androidtestapp.designsystem

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import edu.gatech.cc.cellwatch.androidtestapp.designsystem.Theme.dp

/**
 * A screen whose middle is a list and whose ends are not.
 *
 * [ScreenScaffold] scrolls everything together, which is right for a form and
 * wrong for a list: on History it put the selected run's detail *below* the
 * run list, so choosing a run scrolled its own detail off the bottom, and the
 * more history a user had the further away the answer moved. Here the header
 * and the detail stay put and only the rows move.
 *
 * Mirror of the iOS `ListScreenScaffold`.
 */
class ListScreenScaffold(context: Context) : LinearLayout(context) {

    private val headerColumn = LinearLayout(context).apply {
        orientation = VERTICAL
        setPadding(
            context.dp(Theme.Space.L),
            context.dp(Theme.Space.L),
            context.dp(Theme.Space.L),
            context.dp(Theme.Space.M),
        )
    }

    private val listColumn = LinearLayout(context).apply {
        orientation = VERTICAL
        setPadding(
            context.dp(Theme.Space.L),
            context.dp(Theme.Space.S),
            context.dp(Theme.Space.L),
            context.dp(Theme.Space.S),
        )
    }

    private val actionColumn = LinearLayout(context).apply {
        orientation = VERTICAL
        setPadding(
            context.dp(Theme.Space.L),
            context.dp(Theme.Space.M),
            context.dp(Theme.Space.L),
            context.dp(Theme.Space.M),
        )
    }

    private val listScroll = ScrollView(context).apply {
        isFillViewport = true
        // Without a floor, a tall header plus a long selected-run detail can
        // squeeze the list to nothing on a short screen - and a list of zero
        // height reads as "no history" rather than "scroll me".
        minimumHeight = context.dp(140)
        addView(
            listColumn,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
    }

    init {
        orientation = VERTICAL
        setBackgroundColor(Theme.Palette.BACKGROUND)

        addView(
            headerColumn,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
        addView(listScroll, LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        addView(
            actionColumn,
            LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )
    }

    /** Fixed region above the list. */
    fun addHeader(vararg views: View) {
        views.forEach { headerColumn.append(it, Theme.Space.M) }
    }

    /** Replaces the scrolling region's contents. */
    fun setListItems(views: List<View>) {
        listColumn.removeAllViews()
        views.forEach { listColumn.append(it, Theme.Space.S) }
        listScroll.scrollTo(0, 0)
    }

    /** Pinned below the list. Primary action first. */
    fun addActions(vararg views: View) {
        views.forEach { actionColumn.append(it, Theme.Space.S) }
    }

    /** Preserves whatever size the component asked for; see [ScreenScaffold]. */
    private fun LinearLayout.append(view: View, spacingDp: Int) {
        val params = view.layoutParams as? LayoutParams
            ?: LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        if (childCount > 0) params.topMargin = context.dp(spacingDp)
        addView(view, params)
    }
}
