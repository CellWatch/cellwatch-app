package edu.gatech.cc.cellwatch.domain.fcc

/**
 * Shared FCC-oriented measurement success policy.
 *
 * `success` is submitted verbatim as the FCC `success_flag` on every Download,
 * Upload and Latency Test object (see supabase `fcc_submissions_view`), which
 * the BDC spec defines as "whether the test completed successfully and without
 * a change in state or connectivity". Both halves of that sentence matter:
 *
 *  - *completed*: a run torn down early still transfers some bytes, and the old
 *    `bytesPerSec > 0` test could never fail - a test that died after 200ms was
 *    reported as a full one. Coverage of the requested window is now required.
 *  - *without a change in state or connectivity*: [finalizeMeasurementSuccess].
 *
 * Note that a failed test is still submitted, deliberately. The spec makes
 * fields nullable *because* `success_flag` is false, so failures are expected
 * data - and for a coverage challenge, withholding them would bias the record
 * toward the places where service happens to work.
 */
object MeasurementResultPolicy {

    /**
     * Fraction of the requested duration that must actually be observed for a
     * run to count as completed. Runs routinely end slightly early or late
     * (the server stops on its own schedule and the client drains afterwards),
     * so this rejects truncation rather than policing jitter.
     */
    const val MIN_DURATION_COVERAGE = 0.8

    fun durationCoverageMet(
        measuredDurationMs: Long,
        requestedDurationMs: Long,
    ): Boolean {
        // An unknown or unset requested duration cannot be judged; don't fail
        // a measurement for a missing yardstick.
        if (requestedDurationMs <= 0L) return true
        return measuredDurationMs >= requestedDurationMs * MIN_DURATION_COVERAGE
    }

    fun latencyResultSuccess(
        packetsReceived: Int,
        measuredDurationMs: Long = 0,
        requestedDurationMs: Long = 0,
    ): Boolean =
        packetsReceived > 0 && durationCoverageMet(measuredDurationMs, requestedDurationMs)

    fun throughputResultSuccess(
        activeBytesPerSec: Double,
        measuredDurationMs: Long = 0,
        requestedDurationMs: Long = 0,
    ): Boolean =
        activeBytesPerSec > 0.0 && durationCoverageMet(measuredDurationMs, requestedDurationMs)

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
