package edu.gatech.cc.cellwatch.domain.sync

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class SyncSmokeResultEnvelopeTest {

    private val builder = SyncSmokeEnvelopeBuilder()
    private val formatter = SyncSmokeResultFormatter()

    @Test
    fun mapStart_whenReportMissing_returnsFailure() {
        val envelope = builder.mapStart(hasReport = false, errorMessage = "network down")
        assertEquals(SyncSmokeStatus.FAILURE, envelope.status)
        assertContains(envelope.invariantSummary, "error-message-present=true")
    }

    @Test
    fun measurementComplete_whenUploadTimeSet_returnsSuccess() {
        val envelope = builder.measurementComplete(uploadTimeSet = true, errorMessage = null)
        assertEquals(SyncSmokeStatus.SUCCESS, envelope.status)
        assertContains(envelope.invariantSummary, "measurementCompleteUploadTimeSet=true")
    }

    @Test
    fun phase3_whenMissingPersistence_returnsFailure() {
        val envelope = builder.phase3Sequence(
            measurementCompleteUploadTimeSet = true,
            persistedMeasurements = 0,
            persistedSubmissions = 1,
            errorMessage = "failed",
        )
        assertEquals(SyncSmokeStatus.FAILURE, envelope.status)
        assertContains(envelope.invariantSummary, "persistedMeasurements<=0")
    }

    @Test
    fun formatter_emitsStableShape() {
        val envelope = SyncSmokeResultEnvelope(
            scenario = "demo",
            status = SyncSmokeStatus.SUCCESS,
            message = "ok",
            invariantSummary = "all=true",
        )
        val text = formatter.format(envelope)
        assertContains(text, "smokeEnvelope scenario=demo")
        assertContains(text, "status=SUCCESS")
        assertContains(text, "invariants=all=true")
        assertContains(text, "message=ok")
    }
}
