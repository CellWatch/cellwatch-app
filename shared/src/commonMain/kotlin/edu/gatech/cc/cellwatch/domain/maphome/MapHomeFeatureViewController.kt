package edu.gatech.cc.cellwatch.domain.maphome

import kotlin.math.floor

data class MapHomeMeasurementLocationSnapshot(
    val id: String,
    val title: String,
    val timestampMs: Long,
    val latitude: Double,
    val longitude: Double,
)

data class MapHomePointFeature(
    val id: String,
    val title: String,
    val latitude: Double,
    val longitude: Double,
    val timestampMs: Long,
)

data class MapHomeHexCellFeature(
    val id: String,
    val centerLatitude: Double,
    val centerLongitude: Double,
    val measurementCount: Int,
)

data class MapHomeFeatureState(
    val points: List<MapHomePointFeature>,
    val hexCells: List<MapHomeHexCellFeature>,
    val hasAnyLocationData: Boolean,
    val centerLatitude: Double?,
    val centerLongitude: Double?,
    val summary: String,
)

/**
 * Shared map feature reducer for platform map SDK adapters.
 *
 * Inputs:
 * - lightweight location snapshots (typically sourced from measurement history storage)
 * - current zoom level
 *
 * Outputs:
 * - point features
 * - aggregated grid features (hex-style view)
 * - center/summary metadata for map camera + UX copy
 */
class MapHomeFeatureViewController(
    private val maxPoints: Int = 100,
    private val coarseGridDegrees: Double = 0.08,
    private val fineGridDegrees: Double = 0.03,
    private val fineGridZoomThreshold: Double = 12.0,
) {
    private var snapshots: List<MapHomeMeasurementLocationSnapshot> = emptyList()
    private var zoomLevel: Double = 13.0

    fun reset(): MapHomeFeatureState {
        snapshots = emptyList()
        zoomLevel = 13.0
        return currentState()
    }

    fun loadMeasurements(
        values: List<MapHomeMeasurementLocationSnapshot>,
    ): MapHomeFeatureState {
        snapshots = values.sortedByDescending { it.timestampMs }
        return currentState()
    }

    fun onZoomChanged(zoomLevel: Double): MapHomeFeatureState {
        this.zoomLevel = zoomLevel.coerceAtLeast(0.0)
        return currentState()
    }

    fun currentState(): MapHomeFeatureState {
        val points = snapshots
            .mapNotNull { snapshot ->
                val lat = snapshot.latitude
                val lon = snapshot.longitude
                if (lat.isNaN() || lon.isNaN()) {
                    null
                } else {
                    MapHomePointFeature(
                        id = snapshot.id,
                        title = snapshot.title,
                        latitude = lat,
                        longitude = lon,
                        timestampMs = snapshot.timestampMs,
                    )
                }
            }
            .take(maxPoints)

        if (points.isEmpty()) {
            return MapHomeFeatureState(
                points = emptyList(),
                hexCells = emptyList(),
                hasAnyLocationData = false,
                centerLatitude = null,
                centerLongitude = null,
                summary = "No measurement location points available yet.",
            )
        }

        val centerLatitude = points.map { it.latitude }.average()
        val centerLongitude = points.map { it.longitude }.average()
        val gridSize = if (zoomLevel >= fineGridZoomThreshold) fineGridDegrees else coarseGridDegrees
        val hexCells = points
            .groupBy { point ->
                Pair(
                    centerForBucket(point.latitude, gridSize),
                    centerForBucket(point.longitude, gridSize),
                )
            }
            .map { (bucketCenter, grouped) ->
                MapHomeHexCellFeature(
                    id = "cell:${bucketCenter.first}:${bucketCenter.second}",
                    centerLatitude = bucketCenter.first,
                    centerLongitude = bucketCenter.second,
                    measurementCount = grouped.size,
                )
            }
            .sortedByDescending { it.measurementCount }

        val summary = if (hexCells.size == 1) {
            "Showing ${points.size} point(s) in 1 grid cell."
        } else {
            "Showing ${points.size} point(s) in ${hexCells.size} grid cells."
        }

        return MapHomeFeatureState(
            points = points,
            hexCells = hexCells,
            hasAnyLocationData = true,
            centerLatitude = centerLatitude,
            centerLongitude = centerLongitude,
            summary = summary,
        )
    }

    private fun centerForBucket(value: Double, gridSize: Double): Double {
        if (gridSize <= 0.0) return value
        val bucketStart = floor(value / gridSize) * gridSize
        return bucketStart + (gridSize / 2.0)
    }
}
