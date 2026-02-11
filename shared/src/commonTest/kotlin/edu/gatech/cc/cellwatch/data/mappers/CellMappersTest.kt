package edu.gatech.cc.cellwatch.data.mappers

import edu.gatech.cc.cellwatch.db.CellEntity
import edu.gatech.cc.cellwatch.domain.model.Cell
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CellMappersTest {

    @Test
    fun toDomain_maps_all_fields() {
        val ts = 1_710_000_000_000L
        val entity = CellEntity(
            id = "cell-1",
            timestamp = ts,
            cellId = 42,
            physicalCellId = 7L,
            cellConnection = 3L,
            networkGeneration = "5G",
            networkSubtype = "NR",
            signalStrength = -85L,
            rssi = -60L,
            rsrp = -100L,
            rsrq = -12L,
            sinr = 20L,
            csiRsrp = null,
            csiRsrq = null,
            csiSinr = null,
            cqi = null,
            spectrumBand = "n78",
            spectrumBandwidth = 100.0,
            arfcn = 620000L,
            measurementId = "m-1",
            createdOn = ts,
            updatedOn = ts
        )

        val domain = entity.toDomain()

        assertEquals("cell-1", domain.id)
        assertEquals(Instant.fromEpochMilliseconds(ts), domain.timestamp)
        assertEquals(7, domain.physicalCellId)
        assertEquals(-85, domain.signalStrength)
        assertEquals(100.0f, domain.spectrumBandwidth)
        assertEquals(620000, domain.arfcn)
    }

    @Test
    fun round_trip_preserves_values() {
        val ts = Instant.fromEpochMilliseconds(1_710_000_000_000L)
        val cell = Cell(
            id = "cell-1",
            timestamp = ts,
            cellId = 42,
            physicalCellId = 7,
            cellConnection = 3,
            networkGeneration = "5G",
            networkSubtype = "NR",
            signalStrength = -85,
            rssi = -60,
            rsrp = -100,
            rsrq = -12,
            sinr = 20,
            csiRsrp = null,
            csiRsrq = null,
            csiSinr = null,
            cqi = null,
            spectrumBand = "n78",
            spectrumBandwidth = 100.0f,
            arfcn = 620000,
            measurementId = "m-1",
            createdOn = ts,
            updatedOn = ts
        )

        val row = cell.toRow()
        val back = row.toDomain()

        assertEquals(cell.id, back.id)
        assertEquals(cell.timestamp, back.timestamp)
        assertEquals(cell.physicalCellId, back.physicalCellId)
        assertEquals(cell.signalStrength, back.signalStrength)
        assertEquals(cell.spectrumBandwidth, back.spectrumBandwidth)
        assertEquals(cell.arfcn, back.arfcn)
    }

    @Test
    fun null_fields_are_preserved() {
        val cell = Cell(
            id = "nulls",
            timestamp = null,
            cellId = null,
            physicalCellId = null,
            cellConnection = null,
            networkGeneration = null,
            networkSubtype = null,
            signalStrength = null,
            rssi = null,
            rsrp = null,
            rsrq = null,
            sinr = null,
            csiRsrp = null,
            csiRsrq = null,
            csiSinr = null,
            cqi = null,
            spectrumBand = null,
            spectrumBandwidth = null,
            arfcn = null,
            measurementId = null,
            createdOn = null,
            updatedOn = null
        )

        val row = cell.toRow()
        val back = row.toDomain()

        assertNull(back.timestamp)
        assertNull(back.physicalCellId)
        assertNull(back.spectrumBandwidth)
    }
}