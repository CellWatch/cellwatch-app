package edu.gatech.cc.cellwatch.domain.fcc

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MeasurementFailureMessageTest {

    @Test
    fun theLocateFailureMasqueradeReadsAsUnreachable() {
        // Verbatim from a device run: the M-Lab Locate request timed out, the
        // selector substituted an unreachable placeholder, and this is what the
        // user was shown.
        assertEquals(
            MeasurementFailureMessage.UNREACHABLE,
            MeasurementFailureMessage.forFailure(
                "edu.gatech.cc.cellwatch.msak.shared.MsakException: authorize call failed"
            )
        )
    }

    @Test
    fun locateTimeoutReadsAsUnreachable() {
        assertEquals(
            MeasurementFailureMessage.UNREACHABLE,
            MeasurementFailureMessage.forFailure(
                "HttpRequestTimeoutException: Request timeout has expired " +
                    "[url=https://locate.measurementlab.net/v2/nearest/msak/throughput1]"
            )
        )
    }

    @Test
    fun msakLatencyRunTimeoutReadsAsUnreachable() {
        assertEquals(
            MeasurementFailureMessage.UNREACHABLE,
            MeasurementFailureMessage.forFailure("latency test did not complete within 13000ms")
        )
    }

    @Test
    fun cancellationIsNotDressedUpAsANetworkProblem() {
        assertEquals(
            MeasurementFailureMessage.CANCELLED,
            MeasurementFailureMessage.forFailure("JobCancellationException: Job was cancelled")
        )
    }

    @Test
    fun anUnrecognisedFailureFallsBackWithoutLeakingExceptionText() {
        val message = MeasurementFailureMessage.forFailure(
            "kotlin.IllegalStateException: something nobody anticipated"
        )
        assertEquals(MeasurementFailureMessage.GENERIC, message)
        assertTrue(!message.contains("Exception"), "user-facing text must not carry exception names")
    }

    @Test
    fun nullOrBlankIsStillUsable() {
        assertEquals(MeasurementFailureMessage.GENERIC, MeasurementFailureMessage.forFailure(null))
        assertEquals(MeasurementFailureMessage.GENERIC, MeasurementFailureMessage.forFailure("   "))
    }
}
