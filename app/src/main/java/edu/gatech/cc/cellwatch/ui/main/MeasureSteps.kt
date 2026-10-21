package edu.gatech.cc.cellwatch.ui.main

import edu.gatech.cc.cellwatch.data.model.MeasurementGroup
import edu.gatech.cc.cellwatch.domain.fcc.LatencyTest
import edu.gatech.cc.cellwatch.domain.fcc.ThroughputTest
import edu.gatech.cc.cellwatch.ui.main.MeasureViewModel.MeasureProgress

/** The three numbered steps shown on the measure screen, in the order they run. */
enum class MeasureStep { LATENCY, DOWNLOAD, UPLOAD }

enum class MeasureStepStatus { PENDING, RUNNING, DONE, FAILED }

/**
 * What the step indicator on the measure screen shows.
 *
 * @property statuses one entry per [MeasureStep], in order.
 * @property currentStep the step that is running, or the step the measurement stopped in after an
 *   error. Null before the first step starts, when everything finished, or when the error happened
 *   outside a step.
 * @property fraction overall progress from 0 to 1.
 * @property secondsRemaining estimated seconds until the last step ends, or null when nothing is
 *   running any more.
 */
data class MeasureStepProgress(
    val statuses: List<MeasureStepStatus>,
    val currentStep: MeasureStep?,
    val fraction: Float,
    val secondsRemaining: Int?,
) {
    fun status(step: MeasureStep) = statuses[step.ordinal]
}

object MeasureSteps {
    /**
     * The longest each step is configured to take. The latency test stops [LatencyTest.DURATION_MILLIS]
     * plus msak's grace period after the first echo. A throughput test runs a warmup of up to
     * [ThroughputTest.MAX_WARMUP_MILLIS] (usually shorter) and then [ThroughputTest.MAX_ACTIVE_MILLIS]
     * of measurement. Server lookup, location fixes and HTTP round trips are not configured anywhere
     * and are not counted, so a step can run a little over its budget.
     */
    val STEP_BUDGET_MILLIS: Map<MeasureStep, Long> = mapOf(
        MeasureStep.LATENCY to LatencyTest.DURATION_MILLIS + LatencyTest.MSAK_STOP_GRACE_MILLIS,
        MeasureStep.DOWNLOAD to ThroughputTest.MAX_WARMUP_MILLIS + ThroughputTest.MAX_ACTIVE_MILLIS,
        MeasureStep.UPLOAD to ThroughputTest.MAX_WARMUP_MILLIS + ThroughputTest.MAX_ACTIVE_MILLIS,
    )

    /** Whether a measurement is under way, so the time left keeps changing. */
    fun isRunning(progress: MeasureProgress) = progress in RUNNING

    private val RUNNING = setOf(
        MeasureProgress.START,
        MeasureProgress.LOCATE,
        MeasureProgress.LATENCY,
        MeasureProgress.DOWNLOAD,
        MeasureProgress.UPLOAD,
    )

    fun stepFor(progress: MeasureProgress?): MeasureStep? = when (progress) {
        MeasureProgress.LATENCY -> MeasureStep.LATENCY
        MeasureProgress.DOWNLOAD -> MeasureStep.DOWNLOAD
        MeasureProgress.UPLOAD -> MeasureStep.UPLOAD
        else -> null
    }

    /**
     * Which steps have produced a measurement, and whether it succeeded. A measurement with an
     * unknown result (success == null) counts as done, not failed.
     */
    fun finishedSteps(results: MeasurementGroup?): Map<MeasureStep, Boolean> = buildMap {
        results?.latency?.let { put(MeasureStep.LATENCY, it.success != false) }
        results?.download?.let { put(MeasureStep.DOWNLOAD, it.success != false) }
        results?.upload?.let { put(MeasureStep.UPLOAD, it.success != false) }
    }

    /**
     * @param progress where the measurement is now.
     * @param stoppedDuring for [MeasureProgress.ERROR], where the measurement was when it stopped.
     * @param finished steps that produced a measurement, mapped to whether it succeeded. A step whose
     *   test fails without throwing still produces a (failed) measurement and the sequence carries
     *   on, so several steps can be failed while later ones run.
     * @param phaseElapsedMillis how long the current [progress] has been going.
     */
    fun compute(
        progress: MeasureProgress,
        stoppedDuring: MeasureProgress?,
        finished: Map<MeasureStep, Boolean>,
        phaseElapsedMillis: Long,
        budgets: Map<MeasureStep, Long> = STEP_BUDGET_MILLIS,
    ): MeasureStepProgress {
        val steps = MeasureStep.values()
        val total = steps.sumOf { budgets.getValue(it) }.toFloat()
        val running = isRunning(progress)

        val currentStep = when (progress) {
            MeasureProgress.ERROR -> stepFor(stoppedDuring)?.takeIf { it !in finished }
            else -> stepFor(progress)
        }

        val statuses = steps.map { step ->
            val success = finished[step]
            when {
                success == true -> MeasureStepStatus.DONE
                success == false -> MeasureStepStatus.FAILED
                step == currentStep && progress == MeasureProgress.ERROR -> MeasureStepStatus.FAILED
                step == currentStep -> MeasureStepStatus.RUNNING
                else -> MeasureStepStatus.PENDING
            }
        }

        if (progress == MeasureProgress.END) {
            return MeasureStepProgress(statuses, null, 1f, null)
        }

        // The budgets of the steps before the current one count as used up, even if a step ended
        // early (a throughput warmup often does) -- the bar then jumps forward.
        val elapsed = phaseElapsedMillis.coerceAtLeast(0)
        var usedMillis = 0L
        var remainingMillis = 0L
        val currentIndex = currentStep?.ordinal
        for (step in steps) {
            val budget = budgets.getValue(step)
            when {
                currentIndex == null && progress == MeasureProgress.ERROR ->
                    if (step in finished) usedMillis += budget
                currentIndex == null -> remainingMillis += budget
                step.ordinal < currentIndex -> usedMillis += budget
                step.ordinal == currentIndex -> {
                    val inStep = if (progress == MeasureProgress.ERROR) 0 else elapsed.coerceAtMost(budget)
                    usedMillis += inStep
                    remainingMillis += budget - inStep
                }
                else -> remainingMillis += budget
            }
        }

        return MeasureStepProgress(
            statuses = statuses,
            currentStep = currentStep,
            fraction = (usedMillis / total).coerceIn(0f, 1f),
            secondsRemaining = if (running) ((remainingMillis + 999) / 1000).toInt() else null,
        )
    }
}
