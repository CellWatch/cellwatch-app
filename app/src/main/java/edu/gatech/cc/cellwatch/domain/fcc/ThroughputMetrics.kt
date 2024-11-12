package edu.gatech.cc.cellwatch.domain.fcc

import kotlinx.serialization.Serializable

@Serializable
data class ThroughputMetrics(
    val bytes: Long,
    val usecs: Long,

    /**
     * WARNING! bytesPerSec is not necessarily equal to bytes / (usecs / 1000000). When metrics are
     * an aggregate for several streams, the usecs value reflects the total duration of the test
     * while the bytesPerSec value reflects the sum of the bytesPerSec for each stream, based on its
     * latest throughput update.
     */
    val bytesPerSec: Double,
) {
    override fun toString(): String {
        return "${"%.2f".format(8 * bytesPerSec / 1e6)} Mbps, $bytes bytes, ${usecs / 1e6} secs"
    }
}
