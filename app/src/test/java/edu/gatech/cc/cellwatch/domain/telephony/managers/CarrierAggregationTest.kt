package edu.gatech.cc.cellwatch.domain.telephony.managers

import android.telephony.CellInfo.CONNECTION_NONE
import android.telephony.CellInfo.CONNECTION_PRIMARY_SERVING
import android.telephony.CellInfo.CONNECTION_SECONDARY_SERVING
import edu.gatech.cc.cellwatch.data.model.Cell
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests [isUsingCarrierAggregation] on single reports of the phone's cells. */
class CarrierAggregationTest {
    private val ltePrimary = Cell(cellId = 1, networkGeneration = "4G", cellConnection = CONNECTION_PRIMARY_SERVING)
    private val lteSecondary = Cell(cellId = 2, networkGeneration = "4G", cellConnection = CONNECTION_SECONDARY_SERVING)
    private val nrSecondary = Cell(cellId = 3, networkGeneration = "5G", cellConnection = CONNECTION_SECONDARY_SERVING)
    private val lteNeighbour = Cell(cellId = 4, networkGeneration = "4G", cellConnection = CONNECTION_NONE)
    private val unknownConnection = Cell(cellId = 5, networkGeneration = "4G", cellConnection = null)

    @Test
    fun emptyReportIsNotAggregation() {
        assertFalse(isUsingCarrierAggregation(emptyList()))
    }

    @Test
    fun primaryCellAloneIsNotAggregation() {
        assertFalse(isUsingCarrierAggregation(listOf(ltePrimary)))
    }

    @Test
    fun neighbouringCellsDoNotCount() {
        assertFalse(isUsingCarrierAggregation(listOf(ltePrimary, lteNeighbour, unknownConnection)))
    }

    @Test
    fun primaryWithSecondaryCellIsAggregation() {
        assertTrue(isUsingCarrierAggregation(listOf(ltePrimary, lteNeighbour, lteSecondary)))
    }

    @Test
    fun lteAnchorWithNrSecondaryCounts() {
        // 5G non-standalone (EN-DC) reports the 5G cell as a secondary serving cell, so it counts.
        assertTrue(isUsingCarrierAggregation(listOf(ltePrimary, nrSecondary)))
    }
}
