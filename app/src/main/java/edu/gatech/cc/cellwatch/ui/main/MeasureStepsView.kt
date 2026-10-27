package edu.gatech.cc.cellwatch.ui.main

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import edu.gatech.cc.cellwatch.R
import edu.gatech.cc.cellwatch.databinding.ViewMeasureStepBinding
import edu.gatech.cc.cellwatch.databinding.ViewMeasureStepsBinding
import edu.gatech.cc.cellwatch.ui.main.MeasureViewModel.MeasureProgress

/**
 * The "1 Latency, 2 Download, 3 Upload" indicator at the top of the measure screen: a circle per
 * step, a bar for overall progress, the current step and the estimated time left. What to show is
 * worked out by [MeasureSteps]; this view only draws it.
 */
class MeasureStepsView(
    context: Context,
    attrs: AttributeSet? = null,
): LinearLayout(context, attrs) {
    private val binding: ViewMeasureStepsBinding

    init {
        orientation = VERTICAL
        binding = ViewMeasureStepsBinding.inflate(LayoutInflater.from(context), this)
        for (step in MeasureStep.values()) {
            val b = stepBinding(step)
            b.number.text = (step.ordinal + 1).toString()
            b.label.setText(stepName(step))
        }
    }

    fun render(progress: MeasureProgress, p: MeasureStepProgress) {
        for (step in MeasureStep.values()) {
            renderStep(step, p.status(step))
        }

        renderLine(binding.lineLatencyDownload, p.status(MeasureStep.LATENCY))
        renderLine(binding.lineDownloadUpload, p.status(MeasureStep.DOWNLOAD))

        binding.bar.setIndicatorColor(color(
            if (progress == MeasureProgress.ERROR) R.color.cw_orange else R.color.cw_green
        ))
        binding.bar.setProgressCompat((p.fraction * binding.bar.max).toInt(), true)

        val count = MeasureStep.values().size
        val current = p.currentStep
        val stepText = when {
            progress == MeasureProgress.END && p.statuses.any { it == MeasureStepStatus.FAILED } ->
                context.getString(R.string.measure_steps_some_failed)
            progress == MeasureProgress.END -> context.getString(R.string.measure_steps_all_done)
            progress == MeasureProgress.ERROR && current != null -> context.getString(
                R.string.measure_steps_stopped_at, current.ordinal + 1, count, context.getString(stepName(current))
            )
            progress == MeasureProgress.ERROR -> context.getString(R.string.measure_steps_stopped)
            current != null -> context.getString(
                R.string.measure_steps_current, current.ordinal + 1, count, context.getString(stepName(current))
            )
            else -> context.getString(R.string.measure_steps_preparing)
        }
        // Only touch the text when it changes, so the live region announces each step just once.
        if (binding.stepText.text.toString() != stepText) {
            binding.stepText.text = stepText
        }

        val seconds = p.secondsRemaining
        binding.remainingText.isVisible = seconds != null
        if (seconds != null) {
            binding.remainingText.text = if (seconds <= 1) {
                context.getString(R.string.measure_steps_almost_done)
            } else {
                context.getString(R.string.measure_steps_seconds_left, seconds)
            }
        }
    }

    private fun renderStep(step: MeasureStep, status: MeasureStepStatus) {
        val b = stepBinding(step)
        val dp = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1F, resources.displayMetrics)

        val circle = GradientDrawable().apply { shape = GradientDrawable.OVAL }
        when (status) {
            MeasureStepStatus.PENDING -> {
                circle.setColor(color(R.color.cw_white))
                circle.setStroke((2 * dp).toInt(), color(R.color.cw_grey_light))
            }
            MeasureStepStatus.RUNNING -> {
                circle.setColor(color(R.color.cw_green_light))
                circle.setStroke((2 * dp).toInt(), color(R.color.cw_green))
            }
            MeasureStepStatus.DONE -> circle.setColor(color(R.color.cw_green))
            MeasureStepStatus.FAILED -> circle.setColor(color(R.color.cw_orange))
        }
        b.circle.background = circle

        val finished = status == MeasureStepStatus.DONE || status == MeasureStepStatus.FAILED
        b.number.isVisible = !finished
        b.number.setTextColor(color(
            if (status == MeasureStepStatus.RUNNING) R.color.cw_green else R.color.cw_grey
        ))
        b.icon.isVisible = finished
        if (finished) {
            b.icon.setImageResource(
                if (status == MeasureStepStatus.DONE) R.drawable.fa_solid_check else R.drawable.fa_solid_xmark
            )
        }

        b.label.setTextColor(color(when (status) {
            MeasureStepStatus.PENDING -> R.color.cw_grey
            MeasureStepStatus.RUNNING -> R.color.cw_green
            MeasureStepStatus.DONE -> R.color.cw_black
            MeasureStepStatus.FAILED -> R.color.cw_orange
        }))
        b.label.setTypeface(null, if (status == MeasureStepStatus.RUNNING) Typeface.BOLD else Typeface.NORMAL)

        b.root.contentDescription = context.getString(
            R.string.measure_step_description,
            step.ordinal + 1,
            context.getString(stepName(step)),
            context.getString(when (status) {
                MeasureStepStatus.PENDING -> R.string.measure_step_pending
                MeasureStepStatus.RUNNING -> R.string.measure_step_running
                MeasureStepStatus.DONE -> R.string.measure_step_done
                MeasureStepStatus.FAILED -> R.string.measure_step_failed
            }),
        )
    }

    /** The line after a step turns green once that step is over, whatever its result. */
    private fun renderLine(line: View, before: MeasureStepStatus) {
        val over = before == MeasureStepStatus.DONE || before == MeasureStepStatus.FAILED
        line.setBackgroundColor(color(if (over) R.color.cw_green else R.color.cw_grey_extra_light))
    }

    private fun stepBinding(step: MeasureStep): ViewMeasureStepBinding = when (step) {
        MeasureStep.LATENCY -> binding.stepLatency
        MeasureStep.DOWNLOAD -> binding.stepDownload
        MeasureStep.UPLOAD -> binding.stepUpload
    }

    private fun stepName(step: MeasureStep): Int = when (step) {
        MeasureStep.LATENCY -> R.string.latency
        MeasureStep.DOWNLOAD -> R.string.download
        MeasureStep.UPLOAD -> R.string.upload
    }

    private fun color(id: Int) = ContextCompat.getColor(context, id)
}
