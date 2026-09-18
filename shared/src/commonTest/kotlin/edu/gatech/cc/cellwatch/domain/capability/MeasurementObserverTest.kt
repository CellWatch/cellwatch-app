package edu.gatech.cc.cellwatch.domain.capability

import edu.gatech.cc.cellwatch.domain.fcc.MeasurementResultPolicy
import edu.gatech.cc.cellwatch.domain.model.Cell
import edu.gatech.cc.cellwatch.domain.model.Location
import edu.gatech.cc.cellwatch.domain.model.Measurement
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MeasurementObserverTest {

    private fun cell(generation: String) = Cell(
        id = "c-$generation", timestamp = Instant.fromEpochMilliseconds(1),
        cellId = null, physicalCellId = null, cellConnection = null,
        networkGeneration = generation, networkSubtype = null,
        signalStrength = null, rssi = null, rsrp = null, rsrq = null, sinr = null,
        csiRsrp = null, csiRsrq = null, csiSinr = null, cqi = null,
        spectrumBand = null, spectrumBandwidth = null, arfcn = null,
        measurementId = null, createdOn = null, updatedOn = null,
    )

    private fun measurement() = Measurement(
        id = "m1", groupId = "g1", type = "download", timestamp = Clock.System.now(),
    )

    @Test
    fun observedCellsAndLocationsReachTheMeasurement() {
        val observation = MeasurementObservation(
            cells = listOf(cell("4G"), cell("5G")),
            locations = listOf(
                Location(id = "l1", lat = 1.0, lon = 2.0),
                Location(id = "l2", lat = 3.0, lon = 4.0),
            ),
        )

        val merged = observation.mergeInto(measurement())

        assertEquals(2, merged.cells?.size)
        assertEquals(2, merged.locations?.size, "start and end location must both survive")
    }

    @Test
    fun anEmptyObservationLeavesTheMeasurementAlone() {
        val before = measurement()
        assertEquals(before, MeasurementObservation().mergeInto(before))
    }

    @Test
    fun aNoteSurvivesEvenWhenNothingWasObserved() {
        // iOS always carries a note explaining what it could not see.
        val merged = MeasurementObservation(note = "iOS exposes no cell identity").mergeInto(measurement())
        assertTrue(merged.capabilityNotes?.contains("observation:") == true)
    }

    @Test
    fun aGenerationChangeDuringTheRunFailsTheMeasurement() {
        // The FCC requires a test to stay on one technology generation.
        assertFalse(
            MeasurementResultPolicy.finalizeMeasurementSuccess(
                resultSuccess = true,
                observedGenerations = listOf("5G", "5G", "4G"),
            )!!
        )
    }

    @Test
    fun astableRunIsUnaffected() {
        assertTrue(
            MeasurementResultPolicy.finalizeMeasurementSuccess(
                resultSuccess = true,
                observedGenerations = listOf("5G", "5G", "5G"),
            )!!
        )
    }

    @Test
    fun observingNothingCannotTurnAFailureIntoASuccess() {
        // A platform that observes no generations must not mask a real failure.
        assertFalse(
            MeasurementResultPolicy.finalizeMeasurementSuccess(
                resultSuccess = false,
                observedGenerations = emptyList(),
            )!!
        )
    }

    @Test
    fun theDefaultProviderObserverIsHarmless() = kotlinx.coroutines.runBlocking {
        val observer = NoOpPlatformCapabilityProvider.createObserver()
        observer.start()
        val observation = observer.stop()
        assertTrue(observation.cells.isEmpty())
        assertTrue(observation.generations.isEmpty())
        Unit
    }
}
