package edu.gatech.cc.cellwatch.ui.main

import edu.gatech.cc.cellwatch.data.model.LatencyData
import edu.gatech.cc.cellwatch.data.model.Measurement
import edu.gatech.cc.cellwatch.data.model.MeasurementGroup
import edu.gatech.cc.cellwatch.data.model.UploadDownloadData
import edu.gatech.cc.cellwatch.ui.main.MeasureStepStatus.DONE
import edu.gatech.cc.cellwatch.ui.main.MeasureStepStatus.FAILED
import edu.gatech.cc.cellwatch.ui.main.MeasureStepStatus.PENDING
import edu.gatech.cc.cellwatch.ui.main.MeasureStepStatus.RUNNING
import edu.gatech.cc.cellwatch.ui.main.MeasureViewModel.MeasureProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MeasureStepsTest {
    private val latency = MeasureStep.LATENCY
    private val download = MeasureStep.DOWNLOAD
    private val upload = MeasureStep.UPLOAD

    private fun compute(
        progress: MeasureProgress,
        finished: Map<MeasureStep, Boolean> = emptyMap(),
        elapsedMillis: Long = 0,
        stoppedDuring: MeasureProgress? = null,
    ) = MeasureSteps.compute(progress, stoppedDuring, finished, elapsedMillis)

    @Test
    fun budgetsComeFromTheConfiguredTestDurations() {
        // latency: 5 s of probes + msak's 1 s grace; throughput: up to 10 s warmup + 10 s active
        assertEquals(
            mapOf(latency to 6_000L, download to 20_000L, upload to 20_000L),
            MeasureSteps.STEP_BUDGET_MILLIS,
        )
    }

    @Test
    fun beforeTheFirstStepEverythingIsPendingAndTheWholeBudgetIsLeft() {
        for (progress in listOf(MeasureProgress.START, MeasureProgress.LOCATE)) {
            val p = compute(progress, elapsedMillis = 3_000)
            assertEquals(listOf(PENDING, PENDING, PENDING), p.statuses)
            assertNull(p.currentStep)
            assertEquals(0f, p.fraction, 0f)
            assertEquals(46, p.secondsRemaining)
        }
    }

    @Test
    fun latencyStepCountsDown() {
        val p = compute(MeasureProgress.LATENCY, elapsedMillis = 2_500)
        assertEquals(listOf(RUNNING, PENDING, PENDING), p.statuses)
        assertEquals(latency, p.currentStep)
        assertEquals(2_500f / 46_000f, p.fraction, 1e-6f)
        // 3.5 s left of latency + 40 s of throughput, rounded up
        assertEquals(44, p.secondsRemaining)
    }

    @Test
    fun downloadStepCountsEarlierStepsAsUsedUp() {
        val p = compute(MeasureProgress.DOWNLOAD, mapOf(latency to true), elapsedMillis = 5_000)
        assertEquals(listOf(DONE, RUNNING, PENDING), p.statuses)
        assertEquals(download, p.currentStep)
        assertEquals(11_000f / 46_000f, p.fraction, 1e-6f)
        assertEquals(35, p.secondsRemaining)
    }

    @Test
    fun aStepThatFailedWithoutStoppingTheSequenceIsShownAsFailed() {
        // A failed latency test still produces a measurement and the sequence moves on.
        val p = compute(MeasureProgress.UPLOAD, mapOf(latency to false, download to true), elapsedMillis = 0)
        assertEquals(listOf(FAILED, DONE, RUNNING), p.statuses)
        assertEquals(upload, p.currentStep)
        assertEquals(26_000f / 46_000f, p.fraction, 1e-6f)
        assertEquals(20, p.secondsRemaining)
    }

    @Test
    fun aStepThatRunsOverItsBudgetHoldsAtTheEndOfItsSegment() {
        val inDownload = compute(MeasureProgress.DOWNLOAD, mapOf(latency to true), elapsedMillis = 25_000)
        assertEquals(26_000f / 46_000f, inDownload.fraction, 1e-6f)
        assertEquals(20, inDownload.secondsRemaining)

        val inUpload = compute(MeasureProgress.UPLOAD, mapOf(latency to true, download to true), elapsedMillis = 30_000)
        assertEquals(1f, inUpload.fraction, 0f)
        assertEquals(0, inUpload.secondsRemaining)
        assertEquals(RUNNING, inUpload.status(upload))
    }

    @Test
    fun negativeElapsedTimeIsTreatedAsZero() {
        val p = compute(MeasureProgress.LATENCY, elapsedMillis = -500)
        assertEquals(0f, p.fraction, 0f)
        assertEquals(46, p.secondsRemaining)
    }

    @Test
    fun endShowsEveryStepsResultAndAFullBar() {
        val p = compute(MeasureProgress.END, mapOf(latency to true, download to false, upload to true))
        assertEquals(listOf(DONE, FAILED, DONE), p.statuses)
        assertNull(p.currentStep)
        assertEquals(1f, p.fraction, 0f)
        assertNull(p.secondsRemaining)
    }

    @Test
    fun errorMarksTheStepThatWasRunningAndLeavesLaterStepsPending() {
        val p = compute(
            MeasureProgress.ERROR,
            mapOf(latency to true),
            elapsedMillis = 10_000,
            stoppedDuring = MeasureProgress.DOWNLOAD,
        )
        assertEquals(listOf(DONE, FAILED, PENDING), p.statuses)
        assertEquals(download, p.currentStep)
        assertEquals(6_000f / 46_000f, p.fraction, 1e-6f)
        assertNull(p.secondsRemaining)
    }

    @Test
    fun errorBeforeTheFirstStepMarksNoStep() {
        val p = compute(MeasureProgress.ERROR, stoppedDuring = MeasureProgress.LOCATE)
        assertEquals(listOf(PENDING, PENDING, PENDING), p.statuses)
        assertNull(p.currentStep)
        assertEquals(0f, p.fraction, 0f)
        assertNull(p.secondsRemaining)
    }

    @Test
    fun errorAfterTheLastStepFinishedMarksNoStep() {
        // e.g. saving the FCC submission failed after the upload test had finished
        val p = compute(
            MeasureProgress.ERROR,
            mapOf(latency to true, download to true, upload to true),
            stoppedDuring = MeasureProgress.UPLOAD,
        )
        assertEquals(listOf(DONE, DONE, DONE), p.statuses)
        assertNull(p.currentStep)
        assertEquals(1f, p.fraction, 0f)
    }

    @Test
    fun preShowsNothingRunning() {
        val p = compute(MeasureProgress.PRE)
        assertEquals(listOf(PENDING, PENDING, PENDING), p.statuses)
        assertNull(p.secondsRemaining)
    }

    @Test
    fun finishedStepsReadsSuccessFromTheMeasurements() {
        val group = MeasurementGroup(
            measurement("latency", success = false),
            measurement("download", success = null),
            null,
            null,
        )
        assertEquals(mapOf(latency to false, download to true), MeasureSteps.finishedSteps(group))
        assertEquals(emptyMap<MeasureStep, Boolean>(), MeasureSteps.finishedSteps(null))
    }

    private fun measurement(type: String, success: Boolean?) = Measurement(
        groupId = "group",
        type = type,
        success = success,
        latencyData = if (type == "latency") LatencyData() else null,
        uploadDownloadData = if (type == "latency") null else UploadDownloadData(),
        connectionType = null,
        cellularDataEnabled = null,
    )
}
