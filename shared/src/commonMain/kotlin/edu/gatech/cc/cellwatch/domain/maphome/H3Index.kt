package edu.gatech.cc.cellwatch.domain.maphome

/**
 * Parent and child derivation, by bit masking rather than a library call.
 *
 * h3-kmp exposes only geoToH3, vertices and areNeighborCells, so there is no
 * cellToParent or cellToChildren to call. Deriving them is arithmetic on the
 * index, not geometry, which is why this lives in commonMain and needs no
 * platform support at all.
 *
 * The H3 v4 index layout, most significant bit first:
 *
 *   1  bit   reserved            (63)
 *   4  bits  mode                (59-62)
 *   3  bits  mode-dependent      (56-58)
 *   4  bits  resolution          (52-55)
 *   7  bits  base cell           (45-51)
 *   45 bits  fifteen 3-bit digits (0-44), digit r at bits 45-3r
 *
 * A digit beyond the cell's own resolution is 7, the unused marker.
 */
object H3Index {

    private const val MAX_RESOLUTION = 15
    private const val RESOLUTION_SHIFT = 52
    private const val UNUSED_DIGIT = 7UL

    /** The resolution encoded in a cell, or null if it cannot be parsed. */
    fun resolutionOf(cell: String): Int? =
        cell.toULongOrNull(16)?.let { ((it shr RESOLUTION_SHIFT) and 0xFUL).toInt() }

    /**
     * The ancestor at [resolution], or null if the cell is already coarser.
     *
     * This is `hex_ancestor` in the published schema, which is how the server
     * rolls resolution-9 storage up to resolution-8 reporting. Deriving it the
     * same way on the client is what keeps the two agreeing.
     */
    fun parentOf(cell: String, resolution: Int): String? {
        val value = cell.toULongOrNull(16) ?: return null
        val current = resolutionOf(cell) ?: return null
        if (resolution > current) return null
        if (resolution == current) return cell

        var parent = withResolution(value, resolution)
        for (digit in (resolution + 1)..MAX_RESOLUTION) {
            parent = parent or (UNUSED_DIGIT shl digitShift(digit))
        }
        return parent.toCanonicalCell()
    }

    /**
     * Every descendant at [resolution]. Seven per step down.
     *
     * Empty when the cell is already at or finer than the requested
     * resolution, rather than throwing: callers are rendering, and an empty
     * list draws nothing.
     */
    fun childrenOf(cell: String, resolution: Int): List<String> {
        val value = cell.toULongOrNull(16) ?: return emptyList()
        val current = resolutionOf(cell) ?: return emptyList()
        if (resolution <= current || resolution > MAX_RESOLUTION) return emptyList()

        var cells = listOf(withResolution(value, resolution))
        for (digit in (current + 1)..resolution) {
            val shift = digitShift(digit)
            // Clear the unused marker, then fan out over the seven digits.
            cells = cells.flatMap { parent ->
                val cleared = parent and (UNUSED_DIGIT shl shift).inv()
                (0UL..6UL).map { child -> cleared or (child shl shift) }
            }
        }
        return cells.map { it.toCanonicalCell() }
    }

    /**
     * Zero-padded to sixteen characters, which is how h3-kmp renders an index.
     *
     * `toString(16)` drops the leading zero, so a derived id compared against
     * a library-produced one differs as a string while being the same cell -
     * and cell ids are used as map keys, where that mismatch is silent.
     */
    private fun ULong.toCanonicalCell(): String = toString(16).padStart(16, '0')

    private fun withResolution(value: ULong, resolution: Int): ULong =
        (value and (0xFUL shl RESOLUTION_SHIFT).inv()) or (resolution.toULong() shl RESOLUTION_SHIFT)

    private fun digitShift(digit: Int): Int = 45 - (3 * digit)
}
