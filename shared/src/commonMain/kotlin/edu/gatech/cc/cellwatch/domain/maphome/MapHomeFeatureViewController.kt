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

    /**
     * Whether this cell holds any measurement.
     *
     * frozenApp drew the whole visible grid and used fill to distinguish:
     * an empty cell is outline only, an occupied one is filled. Without the
     * empty cells there is no grid to read a filled one against.
     */
    val hasMeasurements: Boolean get() = measurementCount > 0
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
    private var bounds: H3Bounds? = null
    private var selectedParent: String? = null

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

    /**
     * The visible rectangle, so the grid can cover it.
     *
     * Needed because the overlay tiles the viewport rather than only the
     * cells that happen to contain data; without bounds there is nothing to
     * tile against.
     */
    /**
     * Drills into a cell, or back out of it.
     *
     * frozenApp's behaviour: tapping a parent renders its children inside it
     * and clears the parent's own fill, so the finer breakdown is readable
     * against the coarse grid. Tapping the selected parent again backs out.
     */
    fun onCellSelected(cellId: String): MapHomeFeatureState {
        selectedParent = when {
            selectedParent == cellId -> null
            // Only a parent-resolution cell drills; tapping a child that is
            // already shown should not re-enter a level.
            H3Index.resolutionOf(cellId) == H3Resolution.OVERLAY -> cellId
            else -> null
        }
        return currentState()
    }

    fun clearSelection(): MapHomeFeatureState {
        selectedParent = null
        return currentState()
    }

    fun onBoundsChanged(north: Double, south: Double, east: Double, west: Double): MapHomeFeatureState {
        bounds = H3Bounds(north = north, south = south, east = east, west = west)
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
            // The grid is a property of the viewport, not of the data. It
            // used to be returned empty here, so a device with no
            // measurements - every fresh install - saw a bare map, and
            // panning never produced anything because the early return fired
            // before the tiling did.
            val tiled = tileViewport()
            return MapHomeFeatureState(
                points = emptyList(),
                hexCells = tiled,
                hasAnyLocationData = false,
                centerLatitude = null,
                centerLongitude = null,
                summary = MapHomeCopy.NO_POINTS_YET,
            )
        }

        val centerLatitude = points.map { it.latitude }.average()
        val centerLongitude = points.map { it.longitude }.average()
        val hexCells = aggregateCells(points)

        val summary = MapHomeCopy.showingPoints(points.size, hexCells.size)

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
    /**
     * The empty grid over whatever the map is currently showing.
     *
     * Separate from [aggregateCells] because it has to work with no points at
     * all, which is the state a new install is in and the state the overlay
     * used to draw nothing in.
     */
    private fun tileViewport(): List<MapHomeHexCellFeature> {
        if (!H3Grid.isSupported) return emptyList()
        val covering = bounds?.let { h3CellsCovering(it, H3Resolution.OVERLAY) }.orEmpty()
        return covering.mapNotNull(::emptyCell)
    }

    private fun emptyCell(cell: String): MapHomeHexCellFeature? {
        val boundary = H3Grid.boundaryOf(cell)
        if (boundary.isEmpty()) return null
        return MapHomeHexCellFeature(
            id = cell,
            centerLatitude = boundary.map { it.latitude }.average(),
            centerLongitude = boundary.map { it.longitude }.average(),
            measurementCount = 0,
            boundary = boundary,
        )
    }

    private fun aggregateCells(points: List<MapHomePointFeature>): List<MapHomeHexCellFeature> {
        if (!H3Grid.isSupported) return squareBuckets(points)

        // Always the parent resolution, as frozenApp drew it. Switching the
        // grid to resolution 9 when zoomed in was my own addition and it made
        // drill-down impossible: the cells on screen were already children,
        // so tapping one had nothing finer to reveal. Zoom now affects only
        // how much of the grid fits, not what a cell means.
        val resolution = H3Resolution.OVERLAY
        val grouped = points.groupBy { point ->
            H3Grid.cellAt(point.latitude, point.longitude, resolution)
        }
        // Every cell on screen, not only those holding data: the grid is the
        // point of the overlay, and a lone filled hexagon reads as a marker.
        val tiled = bounds?.let { h3CellsCovering(it, resolution) }.orEmpty()
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
        val occupied = indexed.associateBy { it.id }
        val empty = tiled.filter { it !in occupied }.mapNotNull(::emptyCell)

        val parentGrid = (indexed + empty).sortedByDescending { it.measurementCount }
        val withChildren = expandSelected(parentGrid, points, resolution)
        return (withChildren + squareBuckets(unindexed))
            .sortedByDescending { it.measurementCount }
    }

    /**
     * Replaces the selected cell with its children.
     *
     * The parent itself is dropped rather than drawn underneath: frozenApp
     * clears its fill when selected, and leaving a filled parent behind its
     * own children double-counts the same measurements on screen.
     */
    private fun expandSelected(
        grid: List<MapHomeHexCellFeature>,
        points: List<MapHomePointFeature>,
        resolution: Int,
    ): List<MapHomeHexCellFeature> {
        val parent = selectedParent ?: return grid
        if (resolution != H3Resolution.OVERLAY) return grid
        val childResolution = H3Resolution.STORED

        val childCounts = points
            .mapNotNull { H3Grid.cellAt(it.latitude, it.longitude, childResolution) }
            .groupingBy { it }
            .eachCount()

        val children = H3Index.childrenOf(parent, childResolution).mapNotNull { child ->
            val boundary = H3Grid.boundaryOf(child)
            if (boundary.isEmpty()) {
                null
            } else {
                MapHomeHexCellFeature(
                    id = child,
                    centerLatitude = boundary.map { it.latitude }.average(),
                    centerLongitude = boundary.map { it.longitude }.average(),
                    measurementCount = childCounts[child] ?: 0,
                    boundary = boundary,
                )
            }
        }
        return grid.filterNot { it.id == parent } + children
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
