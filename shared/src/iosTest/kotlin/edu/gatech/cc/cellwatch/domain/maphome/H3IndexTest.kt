package edu.gatech.cc.cellwatch.domain.maphome

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Checks the bit masking against H3 itself.
 *
 * The derivation is arithmetic and would compile and run anywhere, but a
 * self-consistent wrong answer is exactly the failure mode to worry about.
 * These tests cross-check it against geoToH3, so they live where the library
 * exists.
 */
class H3IndexTest {

    private val lat = 33.7756
    private val lon = -84.3963

    private fun cell(resolution: Int) = H3Grid.cellAt(lat, lon, resolution)!!

    @Test
    fun resolutionRoundTripsThroughTheIndex() {
        assertEquals(8, H3Index.resolutionOf(cell(8)))
        assertEquals(9, H3Index.resolutionOf(cell(9)))
    }

    @Test
    fun theDerivedParentIsTheCellTheLibraryIndexesAtThatResolution() {
        // The load-bearing assertion: bit masking must agree with H3 itself,
        // or client aggregation silently diverges from the server's
        // hex_ancestor rollup.
        assertEquals(cell(8), H3Index.parentOf(cell(9), 8))
        assertEquals(cell(7), H3Index.parentOf(cell(9), 7))
        assertEquals(cell(8), H3Index.parentOf(cell(8), 8))
    }

    @Test
    fun aCoarserCellHasNoParentAtAFinerResolution() {
        assertNull(H3Index.parentOf(cell(8), 9))
    }

    @Test
    fun aCellHasSevenChildrenAndContainsTheOneItCameFrom() {
        val children = H3Index.childrenOf(cell(8), 9)

        assertEquals(7, children.size)
        assertContains(children, cell(9))
        // Every child must point back at the parent, or the two derivations
        // disagree with each other.
        children.forEach { child ->
            assertEquals(cell(8), H3Index.parentOf(child, 8), "child $child has the wrong parent")
        }
    }

    @Test
    fun childrenAreRealCellsWithBoundaries() {
        H3Index.childrenOf(cell(8), 9).forEach { child ->
            assertEquals(6, H3Grid.boundaryOf(child).size, "child $child has no hexagon")
        }
    }

    @Test
    fun twoStepsDownGivesFortyNine() {
        assertEquals(49, H3Index.childrenOf(cell(8), 10).size)
    }

    @Test
    fun childrenOfAFinerCellIsEmptyRatherThanThrowing() {
        assertTrue(H3Index.childrenOf(cell(9), 8).isEmpty())
    }
}
