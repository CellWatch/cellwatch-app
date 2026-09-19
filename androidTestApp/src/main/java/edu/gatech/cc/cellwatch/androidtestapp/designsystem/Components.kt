package edu.gatech.cc.cellwatch.androidtestapp.designsystem

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import edu.gatech.cc.cellwatch.androidtestapp.designsystem.Theme.dp

/**
 * The component inventory. Mirror of the iOS `Components`.
 *
 * Screens compose these; they do not build `TextView`/`Button` hierarchies
 * inline. `MainActivity` did the latter - 52 `TextView`s, 61 `Button`s and 68
 * `addView` calls in one 3,322-line file - which is why no two screens match.
 *
 * Built programmatically rather than as XML layouts, which is a deliberate
 * divergence from frozenApp (21 layouts). Two reasons: androidTestApp already
 * has zero XML, so this introduces no second paradigm, and mirroring the iOS
 * factories keeps screen code structurally similar across platforms - which is
 * the whole point of an inventory. Revisit if screens grow complex enough that
 * layout previews start to pay for themselves.
 */
object Components {

    // MARK: Actions

    fun primaryButton(context: Context, title: String): Button =
        filledButton(context, title, Theme.Palette.PRIMARY, Theme.Palette.ON_PRIMARY)

    fun secondaryButton(context: Context, title: String): Button =
        filledButton(context, title, Theme.Palette.SURFACE, Theme.Palette.PRIMARY, outlined = true)

    private fun filledButton(
        context: Context,
        title: String,
        background: Int,
        foreground: Int,
        outlined: Boolean = false,
    ): Button = Button(context).apply {
        text = title
        isAllCaps = false
        setTextColor(foreground)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, Theme.TextSize.HEADING)
        typeface = Typeface.DEFAULT_BOLD
        stateListAnimator = null
        this.background = GradientDrawable().apply {
            setColor(background)
            cornerRadius = context.dp(Theme.Radius.CONTROL).toFloat()
            if (outlined) setStroke(context.dp(1), Theme.Palette.PRIMARY)
        }
        minimumHeight = context.dp(Theme.MIN_TAP_TARGET_DP)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
    }

    // MARK: Text

    fun sectionHeader(context: Context, text: String): TextView = TextView(context).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, Theme.TextSize.TITLE)
        setTextColor(Theme.Palette.TEXT_PRIMARY)
        typeface = Typeface.DEFAULT_BOLD
    }

    fun bodyText(context: Context, text: String, muted: Boolean = false): TextView =
        TextView(context).apply {
            this.text = text
            setTextSize(TypedValue.COMPLEX_UNIT_SP, Theme.TextSize.BODY)
            setTextColor(if (muted) Theme.Palette.TEXT_SECONDARY else Theme.Palette.TEXT_PRIMARY)
        }

    /** Label left, value right. Results and history are mostly these. */
    fun metricRow(context: Context, label: String, value: String): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL

            addView(
                bodyText(context, label, muted = true),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
            )
            addView(
                TextView(context).apply {
                    text = value
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, Theme.TextSize.METRIC)
                    setTextColor(Theme.Palette.TEXT_PRIMARY)
                    typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
                    gravity = Gravity.END
                },
            )
        }

    enum class StatusTone(val accent: Int) {
        NEUTRAL(Theme.Palette.BLUE_LIGHT),
        SUCCESS(Theme.Palette.GREEN),
        WARNING(Theme.Palette.ORANGE),
    }

    /** Boxed message: sync state, validation results, warnings. */
    fun statusCard(context: Context, text: String, tone: StatusTone = StatusTone.NEUTRAL): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            background = GradientDrawable().apply {
                setColor(Theme.Palette.SURFACE)
                cornerRadius = context.dp(Theme.Radius.CARD).toFloat()
                setStroke(context.dp(1), Theme.Palette.BORDER)
            }
            setPadding(context.dp(Theme.Space.M), context.dp(Theme.Space.M), context.dp(Theme.Space.M), context.dp(Theme.Space.M))

            addView(
                View(context).apply {
                    background = GradientDrawable().apply {
                        setColor(tone.accent)
                        cornerRadius = context.dp(2).toFloat()
                    }
                },
                LinearLayout.LayoutParams(context.dp(Theme.Space.XS), ViewGroup.LayoutParams.MATCH_PARENT),
            )
            addView(
                bodyText(context, text),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = context.dp(Theme.Space.M)
                },
            )
        }

    // MARK: Input

    fun formField(context: Context, placeholder: String, inputType: Int = InputType.TYPE_CLASS_TEXT): EditText =
        EditText(context).apply {
            hint = placeholder
            this.inputType = inputType
            setTextSize(TypedValue.COMPLEX_UNIT_SP, Theme.TextSize.BODY)
            setTextColor(Theme.Palette.TEXT_PRIMARY)
            setHintTextColor(Theme.Palette.TEXT_MUTED)
            background = GradientDrawable().apply {
                setColor(Theme.Palette.SURFACE)
                cornerRadius = context.dp(Theme.Radius.CONTROL).toFloat()
                setStroke(context.dp(1), Theme.Palette.BORDER)
            }
            setPadding(context.dp(Theme.Space.M), context.dp(Theme.Space.M), context.dp(Theme.Space.M), context.dp(Theme.Space.M))
            minimumHeight = context.dp(Theme.MIN_TAP_TARGET_DP)
        }

    // MARK: State

    fun progressHeader(context: Context, title: String, progressPercent: Int): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(sectionHeader(context, title))
            addView(
                ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
                    max = 100
                    progress = progressPercent
                    progressTintList = android.content.res.ColorStateList.valueOf(Theme.Palette.PRIMARY)
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = context.dp(Theme.Space.S) },
            )
        }

    /** Shown instead of an empty list; a blank screen reads as a bug. */
    fun emptyState(context: Context, message: String): View =
        bodyText(context, message, muted = true).apply {
            gravity = Gravity.CENTER
            setPadding(context.dp(Theme.Space.L), context.dp(Theme.Space.XL), context.dp(Theme.Space.L), context.dp(Theme.Space.XL))
        }

    /** One entry in a list: history items, settings rows. */
    fun listRow(context: Context, title: String, subtitle: String?, accessory: String? = null): View =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = context.dp(Theme.MIN_TAP_TARGET_DP)
            setPadding(0, context.dp(Theme.Space.M), 0, context.dp(Theme.Space.M))

            val textColumn = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(bodyText(context, title))
                if (subtitle != null) {
                    addView(
                        TextView(context).apply {
                            text = subtitle
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, Theme.TextSize.CAPTION)
                            setTextColor(Theme.Palette.TEXT_MUTED)
                        },
                    )
                }
            }
            addView(textColumn, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

            if (accessory != null) {
                addView(
                    TextView(context).apply {
                        text = accessory
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, Theme.TextSize.CAPTION)
                        setTextColor(Theme.Palette.TEXT_SECONDARY)
                    },
                )
            }
        }

    fun divider(context: Context): View = View(context).apply {
        setBackgroundColor(Theme.Palette.BORDER)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, context.dp(1))
    }
}
