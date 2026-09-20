package edu.gatech.cc.cellwatch.domain.maphome

/** A vertex of a hexagon boundary. */
data class H3Vertex(val latitude: Double, val longitude: Double)

/**
 * The two H3 operations this app needs, behind expect/actual.
 *
 * Only two, because parent/child derivation is bit masking rather than a
 * library call, and nothing here needs neighbours or distances.
 *
 * `expect` rather than a commonMain dependency because h3-kmp publishes
 * android and ios variants and no jvm one; the jvm target exists for harness
 * and unit tests, which have no map.
 */
expect object H3Grid {
    /** True when this target can compute cells at all. */
    val isSupported: Boolean

    /** The cell containing a point, as a hex string, or null if unsupported. */
    fun cellAt(latitude: Double, longitude: Double, resolution: Int): String?

    /** The cell's vertices, in order. Empty if unsupported or unknown. */
    fun boundaryOf(cell: String): List<H3Vertex>
}

/** The visible map rectangle, for tiling. */
data class H3Bounds(
    val north: Double,
    val south: Double,
    val east: Double,
    val west: Double,
)

/**
 * Every cell covering [bounds], as frozenApp's `polyfill` produced.
 *
 * Derived by sampling rather than called: h3-kmp exposes only geoToH3,
 * vertices and areNeighborCells - there is no polyfill and no cellToChildren -
 * so the lattice is walked at a spacing well under one cell and the resulting
 * indexes deduplicated.
 *
 * The sample step is a fraction of the cell's own span, so every cell whose
 * interior meets the viewport contains at least one sample. Bounds are
 * expanded by one cell first, so cells clipped by the screen edge - which are
 * exactly the ones a user is about to pan onto - are not missed.
 *
 * Capped, because a low zoom over a wide area would otherwise ask for
 * hundreds of thousands of cells and stall the map.
 */
fun h3CellsCovering(bounds: H3Bounds, resolution: Int, maxCells: Int = 400): List<String> {
    if (!H3Grid.isSupported) return emptyList()

    // Approximate edge-to-edge span of one cell in degrees of latitude.
    val span = when {
        resolution >= 9 -> 0.0035
        resolution >= 8 -> 0.0092
        else -> 0.024
    }
    val step = span / 3.0

    val north = (bounds.north + span).coerceAtMost(90.0)
    val south = (bounds.south - span).coerceAtLeast(-90.0)
    val east = bounds.east + span
    val west = bounds.west - span

    val rows = ((north - south) / step).toInt() + 1
    val columns = ((east - west) / step).toInt() + 1
    // Refuse rather than grind: the caller shows nothing, which is honest at
    // a zoom where individual cells would be sub-pixel anyway.
    if (rows.toLong() * columns.toLong() > 40_000L) return emptyList()

    val cells = LinkedHashSet<String>()
    var row = 0
    while (row < rows) {
        var column = 0
        val latitude = south + (row * step)
        while (column < columns) {
            val longitude = west + (column * step)
            H3Grid.cellAt(latitude, longitude, resolution)?.let(cells::add)
            if (cells.size > maxCells) return cells.toList()
            column++
        }
        row++
    }
    return cells.toList()
}

/**
 * The resolutions this project uses.
 *
 * 8 is what frozenApp drew its overlay at; 9 is what the published schema
 * stores as `center_hex9`. They are named here so a magic number cannot drift
 * apart from the database.
 */
object H3Resolution {
    const val OVERLAY = 8
    const val STORED = 9
}
