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
