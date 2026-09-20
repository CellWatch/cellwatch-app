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
    /**
     * The cell outline, in order, or empty when H3 is unavailable.
     *
     * Absent until now, which is why no platform could draw a hexagon however
     * the icon was configured: there was no geometry to draw. Callers that
     * find this empty should fall back to plotting the centre.
     */
    val boundary: List<H3Vertex> = emptyList(),
) {
    val hasBoundary: Boolean get() = boundary.size >= 3
}

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
/**
 * Legacy direct-access controller. **Product screens must not use this** - they
 * use [MapHomeViewModel], which is the screen's single entry point (Rule 1 in
 * APP_LAYER_ARCHITECTURE_CONTRACT.md).
 *
 * Still public only because the test harnesses construct it directly, and those
 * are deliberately left alone. It is not duplicated logic: [MapHomeViewModel]
 * wraps this same instance rather than reimplementing it.
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
        val hexCells = aggregateCells(points)

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

    /**
     * Groups points into H3 cells, with the geometry needed to draw them.
     *
     * Zoom picks the resolution rather than a bucket size in degrees: a
     * degree-sized square is not a hexagon and does not line up with the
     * `center_hex9` values the database already stores, so the old buckets
     * could never have agreed with the server's aggregation.
     *
     * Falls back to the previous square buckets where H3 is unavailable, so
     * the jvm target and any future platform still produce something sane
     * rather than an empty map.
     */
    private fun aggregateCells(points: List<MapHomePointFeature>): List<MapHomeHexCellFeature> {
        if (!H3Grid.isSupported) return squareBuckets(points)

        val resolution = if (zoomLevel >= fineGridZoomThreshold) {
            H3Resolution.STORED
        } else {
            H3Resolution.OVERLAY
        }
        val grouped = points.groupBy { point ->
            H3Grid.cellAt(point.latitude, point.longitude, resolution)
        }
        // A null key means H3 declined this point; those fall back rather than
        // being silently dropped off the map.
        val unindexed = grouped[null].orEmpty()
        val indexed = grouped.filterKeys { it != null }.map { (cell, members) ->
            val boundary = H3Grid.boundaryOf(cell!!)
            MapHomeHexCellFeature(
                id = cell,
                // The centroid of the boundary, not of the members: a cell
                // drawn around its members' average would sit off its own
                // outline wherever the points cluster to one side.
                centerLatitude = boundary.takeIf { it.isNotEmpty() }?.map { it.latitude }?.average()
                    ?: members.map { it.latitude }.average(),
                centerLongitude = boundary.takeIf { it.isNotEmpty() }?.map { it.longitude }?.average()
                    ?: members.map { it.longitude }.average(),
                measurementCount = members.size,
                boundary = boundary,
            )
        }
        return (indexed + squareBuckets(unindexed)).sortedByDescending { it.measurementCount }
    }

    private fun squareBuckets(points: List<MapHomePointFeature>): List<MapHomeHexCellFeature> {
        if (points.isEmpty()) return emptyList()
        val gridSize = if (zoomLevel >= fineGridZoomThreshold) fineGridDegrees else coarseGridDegrees
        return points
            .groupBy {
                Pair(centerForBucket(it.latitude, gridSize), centerForBucket(it.longitude, gridSize))
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
    }

    private fun centerForBucket(value: Double, gridSize: Double): Double {
        if (gridSize <= 0.0) return value
        val bucketStart = floor(value / gridSize) * gridSize
        return bucketStart + (gridSize / 2.0)
    }
}
