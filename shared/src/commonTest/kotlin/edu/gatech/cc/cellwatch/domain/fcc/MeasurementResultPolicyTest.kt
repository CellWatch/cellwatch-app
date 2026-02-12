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
}
