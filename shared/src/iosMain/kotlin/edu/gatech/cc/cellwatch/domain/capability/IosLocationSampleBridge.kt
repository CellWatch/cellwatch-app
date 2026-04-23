package edu.gatech.cc.cellwatch.domain.capability

import edu.gatech.cc.cellwatch.domain.model.Location
import kotlinx.datetime.Instant

data class IosLocationSample(
    val timestamp: Instant?,
    val lat: Double,
    val lon: Double,
    val accuracy: Double? = null,
    val speed: Double? = null,
    val speedAccuracy: Double? = null,
    val heading: Double? = null,
)

object IosLocationSampleBridge {
    private var latestSample: IosLocationSample? = null

    fun updateSample(
        timestampEpochMillis: Long?,
        lat: Double,
        lon: Double,
        accuracy: Double?,
        speed: Double?,
        speedAccuracy: Double?,
        heading: Double?,
    ) {
        latestSample = IosLocationSample(
            timestamp = timestampEpochMillis?.let(Instant::fromEpochMilliseconds),
            lat = lat,
            lon = lon,
            accuracy = accuracy,
            speed = speed,
            speedAccuracy = speedAccuracy,
            heading = heading,
        )
    }

    fun clearSample() {
        latestSample = null
    }

    internal fun currentSample(): IosLocationSample? = latestSample
}
