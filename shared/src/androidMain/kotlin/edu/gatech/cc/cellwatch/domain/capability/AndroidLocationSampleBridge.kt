package edu.gatech.cc.cellwatch.domain.capability

import kotlinx.datetime.Instant

data class AndroidLocationSample(
    val timestamp: Instant? = null,
    val lat: Double,
    val lon: Double,
    val accuracy: Double? = null,
    val speed: Double? = null,
    val speedAccuracy: Double? = null,
    val heading: Double? = null,
)

object AndroidLocationSampleBridge {
    private var latestSample: AndroidLocationSample? = null

    fun updateSample(sample: AndroidLocationSample) {
        latestSample = sample
    }

    fun clearSample() {
        latestSample = null
    }

    internal fun currentSample(): AndroidLocationSample? = latestSample
}
