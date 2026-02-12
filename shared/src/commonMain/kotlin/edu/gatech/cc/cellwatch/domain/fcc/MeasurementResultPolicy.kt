package edu.gatech.cc.cellwatch.domain.fcc

/**
 * Shared FCC-oriented measurement success policy extracted from legacy Android logic:
 * - Latency tests are considered successful when at least one packet is received.
 * - Throughput tests are considered successful when measured active throughput is > 0.
 * - Final success is forced to false when observed network generations change mid-test.
 */
object MeasurementResultPolicy {
    fun latencyResultSuccess(packetsReceived: Int): Boolean = packetsReceived > 0

    fun throughputResultSuccess(activeBytesPerSec: Double): Boolean = activeBytesPerSec > 0.0

    fun finalizeMeasurementSuccess(
        resultSuccess: Boolean?,
        observedGenerations: List<String?>,
    ): Boolean? {
        if (!isGenerationStable(observedGenerations)) return false
        return resultSuccess
    }

    fun isGenerationStable(observedGenerations: List<String?>): Boolean {
        if (observedGenerations.isEmpty()) return true
        val first = observedGenerations.first()
        return observedGenerations.all { it == first }
    }
}
