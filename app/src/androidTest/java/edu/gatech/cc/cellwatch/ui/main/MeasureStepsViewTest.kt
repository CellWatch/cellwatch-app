package edu.gatech.cc.cellwatch.ui.main

import android.view.View
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import edu.gatech.cc.cellwatch.R
import edu.gatech.cc.cellwatch.ui.UiTestSupport.context
import edu.gatech.cc.cellwatch.ui.UiTestSupport.finishAllActivities
import edu.gatech.cc.cellwatch.ui.main.MeasureStepStatus.DONE
import edu.gatech.cc.cellwatch.ui.main.MeasureStepStatus.FAILED
import edu.gatech.cc.cellwatch.ui.main.MeasureStepStatus.PENDING
import edu.gatech.cc.cellwatch.ui.main.MeasureStepStatus.RUNNING
import edu.gatech.cc.cellwatch.ui.main.MeasureViewModel.MeasureProgress
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The three steps shown while a test runs: latency, download and upload. */
@RunWith(AndroidJUnit4::class)
class MeasureStepsViewTest {
    @After
    fun closeScreens() = finishAllActivities()

    @Test
    fun beforeTheFirstStepItsGettingReady() {
        val shown = render(MeasureProgress.LOCATE, progress(PENDING, PENDING, PENDING, current = null, seconds = 30))

        assertEquals(string(R.string.measure_steps_preparing), shown.stepText)
        assertEquals(context.getString(R.string.measure_steps_seconds_left, 30), shown.remaining)
        assertEquals(description(1, R.string.latency, R.string.measure_step_pending), shown.descriptions[0])
    }

    @Test
    fun runningStepIsNamedWithItsNumber() {
        val shown = render(MeasureProgress.LATENCY, progress(RUNNING, PENDING, PENDING, current = MeasureStep.LATENCY, seconds = 1))

        assertEquals(context.getString(R.string.measure_steps_current, 1, 3, string(R.string.latency)), shown.stepText)
        assertEquals(string(R.string.measure_steps_almost_done), shown.remaining)
        assertEquals(description(1, R.string.latency, R.string.measure_step_running), shown.descriptions[0])
    }

    @Test
    fun finishedStepShowsAnIconInsteadOfItsNumber() {
        val shown = render(MeasureProgress.DOWNLOAD, progress(DONE, RUNNING, PENDING, current = MeasureStep.DOWNLOAD, seconds = null))

        assertEquals(context.getString(R.string.measure_steps_current, 2, 3, string(R.string.download)), shown.stepText)
        assertNull("no time estimate", shown.remaining)
        assertTrue(shown.latencyIconShown)
        assertFalse(shown.latencyNumberShown)
        assertEquals(description(1, R.string.latency, R.string.measure_step_done), shown.descriptions[0])
    }

    @Test
    fun completedTestSaysWhetherEveryStepSucceeded() {
        val allDone = render(MeasureProgress.END, progress(DONE, DONE, DONE, current = null, seconds = null))
        assertEquals(string(R.string.measure_steps_all_done), allDone.stepText)

        val someFailed = render(MeasureProgress.END, progress(DONE, FAILED, DONE, current = null, seconds = null))
        assertEquals(string(R.string.measure_steps_some_failed), someFailed.stepText)
        assertEquals(description(2, R.string.download, R.string.measure_step_failed), someFailed.descriptions[1])
    }

    @Test
    fun stoppedTestSaysWhereItStopped() {
        val atUpload = render(MeasureProgress.ERROR, progress(DONE, DONE, FAILED, current = MeasureStep.UPLOAD, seconds = null))
        assertEquals(context.getString(R.string.measure_steps_stopped_at, 3, 3, string(R.string.upload)), atUpload.stepText)

        val beforeAnyStep = render(MeasureProgress.ERROR, progress(PENDING, PENDING, PENDING, current = null, seconds = null))
        assertEquals(string(R.string.measure_steps_stopped), beforeAnyStep.stepText)
    }

    private class Shown(
        val stepText: String,
        val remaining: String?,
        val descriptions: List<String>,
        val latencyIconShown: Boolean,
        val latencyNumberShown: Boolean,
    )

    private fun progress(
        latency: MeasureStepStatus,
        download: MeasureStepStatus,
        upload: MeasureStepStatus,
        current: MeasureStep?,
        seconds: Int?,
    ) = MeasureStepProgress(listOf(latency, download, upload), current, fraction = 0.5f, secondsRemaining = seconds)

    // Renders a fresh steps view, as the measuring screen does on every update.
    private fun render(state: MeasureProgress, p: MeasureStepProgress): Shown {
        lateinit var shown: Shown
        ActivityScenario.launch(AboutActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val view = MeasureStepsView(activity)
                view.render(state, p)
                val steps = listOf(R.id.stepLatency, R.id.stepDownload, R.id.stepUpload).map { view.findViewById<View>(it) }
                val remaining = view.findViewById<TextView>(R.id.remainingText)
                shown = Shown(
                    stepText = view.findViewById<TextView>(R.id.stepText).text.toString(),
                    remaining = if (remaining.isVisible) remaining.text.toString() else null,
                    descriptions = steps.map { it.contentDescription.toString() },
                    latencyIconShown = steps[0].findViewById<View>(R.id.icon).isVisible,
                    latencyNumberShown = steps[0].findViewById<View>(R.id.number).isVisible,
                )
            }
        }
        return shown
    }

    private fun string(id: Int) = context.getString(id)

    private fun description(number: Int, step: Int, status: Int) =
        context.getString(R.string.measure_step_description, number, string(step), string(status))
}
