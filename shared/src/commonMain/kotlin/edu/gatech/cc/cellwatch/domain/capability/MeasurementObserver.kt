package edu.gatech.cc.cellwatch.domain.capability

import edu.gatech.cc.cellwatch.domain.model.Cell
import edu.gatech.cc.cellwatch.domain.model.Location

/**
 * What was observed across a whole measurement, rather than at one instant.
 *
 * The FCC asks for cellular information "measured during the speed test" and
 * defines success_flag as completing "without a change in state or
 * connectivity". Neither can be answered by a single sample taken before the
 * test starts, which is all the app collected previously.
 */
data class MeasurementObservation(
    val cells: List<Cell> = emptyList(),
    /**
     * Network generation seen over the run, in order. More than one distinct
     * value means the radio changed technology mid-test, which the FCC treats
     * as an unsuccessful measurement.
     */
    val generations: List<String> = emptyList(),
    /** Location at the start and at the end, when available. */
    val locations: List<Location> = emptyList(),
    val note: String? = null,
)

/**
 * Watches telephony and location for the duration of one measurement.
 *
 * [start] and [stop] bracket a single test. Implementations must tolerate
 * [stop] being called without [start], and must not throw: a measurement is
 * worth more than its metadata, so observation failures degrade to an empty
 * observation with a note rather than failing the run.
 */
interface MeasurementObserver {
    suspend fun start()
    suspend fun stop(): MeasurementObservation
}

object NoOpMeasurementObserver : MeasurementObserver {
    override suspend fun start() = Unit
    override suspend fun stop(): MeasurementObservation =
        MeasurementObservation(note = "measurement observation not wired for this platform/runtime")
}

/**
 * Merges an observation into a measurement.
 *
 * Observation wins over a single-instant snapshot for cells and locations,
 * because it covers the window the spec actually asks about. The capability
 * enricher only fills fields that are still absent, so ordering matters:
 * merge the observation first, enrich afterwards.
 */
fun MeasurementObservation.mergeInto(
    measurement: edu.gatech.cc.cellwatch.domain.model.Measurement,
): edu.gatech.cc.cellwatch.domain.model.Measurement {
    if (cells.isEmpty() && locations.isEmpty() && note == null) return measurement
    val mergedNotes = listOfNotNull(measurement.capabilityNotes, note?.let { "observation:$it" })
        .takeIf { it.isNotEmpty() }?.joinToString(" | ")
    return measurement.copy(
        cells = cells.takeIf { it.isNotEmpty() } ?: measurement.cells,
        locations = locations.takeIf { it.isNotEmpty() } ?: measurement.locations,
        capabilityNotes = mergedNotes,
    )
}
