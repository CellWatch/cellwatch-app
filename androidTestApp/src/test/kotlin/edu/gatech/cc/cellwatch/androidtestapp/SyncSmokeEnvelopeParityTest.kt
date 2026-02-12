package edu.gatech.cc.cellwatch.androidtestapp

import edu.gatech.cc.cellwatch.domain.sync.SyncSmokeEnvelopeBuilder
import edu.gatech.cc.cellwatch.domain.sync.SyncSmokeResultFormatter
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncSmokeEnvelopeParityTest {

    private val builder = SyncSmokeEnvelopeBuilder()
    private val formatter = SyncSmokeResultFormatter()

    @Test
    fun envelopeTextShape_successAndFailure_containsCanonicalFields() {
        val success = formatter.format(
            builder.measurementComplete(
                uploadTimeSet = true,
                errorMessage = null,
            )
        )
        assertTrue(success.contains("smokeEnvelope scenario="))
        assertTrue(success.contains("status=SUCCESS"))
        assertTrue(success.contains("invariants="))
        assertTrue(success.contains("message="))

        val failure = formatter.format(
            builder.failure(
                scenario = "measurement-complete-sync",
                errorMessage = "synthetic failure",
            )
        )
        assertTrue(failure.contains("smokeEnvelope scenario=measurement-complete-sync"))
        assertTrue(failure.contains("status=FAILURE"))
        assertTrue(failure.contains("invariants="))
        assertTrue(failure.contains("message=synthetic failure"))
    }
}
