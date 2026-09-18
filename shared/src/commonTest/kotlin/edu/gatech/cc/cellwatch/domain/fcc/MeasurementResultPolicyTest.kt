package edu.gatech.cc.cellwatch.domain.fcc

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MeasurementResultPolicyTest {

    @Test
    fun latencyResultSuccess_requiresAnyReceivedPacket() {
        assertFalse(MeasurementResultPolicy.latencyResultSuccess(packetsReceived = 0))
        assertTrue(MeasurementResultPolicy.latencyResultSuccess(packetsReceived = 1))
        assertTrue(MeasurementResultPolicy.latencyResultSuccess(packetsReceived = 10))
    }

    @Test
    fun throughputResultSuccess_requiresPositiveActiveThroughput() {
        assertFalse(MeasurementResultPolicy.throughputResultSuccess(activeBytesPerSec = 0.0))
        assertFalse(MeasurementResultPolicy.throughputResultSuccess(activeBytesPerSec = -1.0))
        assertTrue(MeasurementResultPolicy.throughputResultSuccess(activeBytesPerSec = 0.01))
    }

    @Test
    fun isGenerationStable_handlesEmptyAndSingleValues() {
        assertTrue(MeasurementResultPolicy.isGenerationStable(emptyList()))
        assertTrue(MeasurementResultPolicy.isGenerationStable(listOf("4G")))
        assertTrue(MeasurementResultPolicy.isGenerationStable(listOf(null)))
    }

    @Test
    fun isGenerationStable_detectsGenerationTransitions() {
        assertFalse(MeasurementResultPolicy.isGenerationStable(listOf("4G", "5G")))
        assertFalse(MeasurementResultPolicy.isGenerationStable(listOf(null, "5G")))
        assertFalse(MeasurementResultPolicy.isGenerationStable(listOf("5G", null)))
    }

    @Test
    fun finalizeMeasurementSuccess_forcesFailureWhenGenerationChanges() {
        assertEquals(
            false,
            MeasurementResultPolicy.finalizeMeasurementSuccess(
                resultSuccess = true,
                observedGenerations = listOf("4G", "5G"),
            ),
        )
        assertEquals(
            false,
            MeasurementResultPolicy.finalizeMeasurementSuccess(
                resultSuccess = null,
                observedGenerations = listOf("4G", "5G"),
            ),
        )
    }

    @Test
    fun finalizeMeasurementSuccess_returnsUnderlyingResultWhenGenerationStable() {
        assertEquals(
            true,
            MeasurementResultPolicy.finalizeMeasurementSuccess(
                resultSuccess = true,
                observedGenerations = listOf("5G", "5G"),
            ),
        )
        assertEquals(
            false,
            MeasurementResultPolicy.finalizeMeasurementSuccess(
                resultSuccess = false,
                observedGenerations = listOf("LTE", "LTE"),
            ),
        )
        assertEquals(
            null,
            MeasurementResultPolicy.finalizeMeasurementSuccess(
                resultSuccess = null,
                observedGenerations = listOf("LTE", "LTE"),
            ),
        )
    }

    // --- duration coverage: a truncated run must not report success ---

    @Test
    fun throughputTruncatedWellShortOfTheRequestedWindowIsNotSuccess() {
        // The symptom that prompted this: an upload phase whose last recorded
        // telemetry was 376ms into a requested 5s still reported success.
        assertFalse(
            MeasurementResultPolicy.throughputResultSuccess(
                activeBytesPerSec = 1_000_000.0,
                measuredDurationMs = 376,
                requestedDurationMs = 5_000,
            )
        )
    }

    @Test
    fun throughputRunningTheFullWindowIsSuccess() {
        assertTrue(
            MeasurementResultPolicy.throughputResultSuccess(
                activeBytesPerSec = 1_000_000.0,
                measuredDurationMs = 5_100,
                requestedDurationMs = 5_000,
            )
        )
    }

    @Test
    fun throughputEndingSlightlyEarlyIsStillSuccess() {
        // Runs routinely end a little early; the threshold rejects truncation,
        // not jitter.
        assertTrue(
            MeasurementResultPolicy.throughputResultSuccess(
                activeBytesPerSec = 1_000_000.0,
                measuredDurationMs = 4_500,
                requestedDurationMs = 5_000,
            )
        )
    }

    @Test
    fun throughputWithNoBytesIsNotSuccessEvenWhenItRanTheFullWindow() {
        assertFalse(
            MeasurementResultPolicy.throughputResultSuccess(
                activeBytesPerSec = 0.0,
                measuredDurationMs = 5_000,
                requestedDurationMs = 5_000,
            )
        )
    }

    @Test
    fun latencyTruncatedRunIsNotSuccess() {
        assertFalse(
            MeasurementResultPolicy.latencyResultSuccess(
                packetsReceived = 12,
                measuredDurationMs = 400,
                requestedDurationMs = 3_000,
            )
        )
    }

    @Test
    fun latencyOvershootingTheRequestedWindowIsSuccess() {
        // The server sends for a fixed 5s regardless of the requested duration,
        // so measuring longer than requested is normal and must not fail.
        assertTrue(
            MeasurementResultPolicy.latencyResultSuccess(
                packetsReceived = 236,
                measuredDurationMs = 5_500,
                requestedDurationMs = 3_000,
            )
        )
    }

    @Test
    fun unknownRequestedDurationCannotBeJudgedSoDoesNotFail() {
        assertTrue(MeasurementResultPolicy.durationCoverageMet(measuredDurationMs = 0, requestedDurationMs = 0))
    }
}
