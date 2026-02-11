package edu.gatech.cc.cellwatch.db

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class CellQueriesIosTest {

    private lateinit var driver: NativeSqliteDriver
    private lateinit var db: CellwatchDatabase

    @BeforeTest
    fun setUp() {
        driver = NativeSqliteDriver(CellwatchDatabase.Schema, ":memory:")
        db = CellwatchDatabase(driver)

        // Ensure a clean slate for every test run
        driver.execute(null, "DELETE FROM CellEntity", 0) {}
        driver.execute(null, "DELETE FROM MeasurementEntity", 0) {}
    }

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    @Test
    fun insert_and_select_by_id() {
        val id = "cell-1"
        val now = 1_710_000_000L

        db.cellQueries.insertOrReplaceCell(
            id = id,
            timestamp = now,
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
            spectrumBandwidth = 100.0,
            arfcn = 620000,
            measurementId = "m-1",
            createdOn = now,
            updatedOn = now
        )

        val row = db.cellQueries.selectCellById(id).executeAsOneOrNull()
        assertNotNull(row)
        assertEquals(id, row!!.id)
        assertEquals(now, row.timestamp)
        assertEquals("n78", row.spectrumBand)
    }

    @Test
    fun select_by_measurement_orders_by_timestamp_desc() {
        val mId = "measure-xyz"

        fun insert(id: String, ts: Long) {
            db.cellQueries.insertOrReplaceCell(
                id = id,
                timestamp = ts,
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
                measurementId = mId,
                createdOn = ts,
                updatedOn = ts
            )
        }

        insert("c1", 100L)
        insert("c2", 300L)
        insert("c3", 200L)

        val rows = db.cellQueries.selectCellsByMeasurement(mId).executeAsList()
        assertEquals(listOf("c2", "c3", "c1"), rows.map { it.id })
    }

    @Test
    fun delete_by_id_removes_row() {
        val id = "to-delete"
        db.cellQueries.insertOrReplaceCell(
            id = id,
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

        assertNotNull(db.cellQueries.selectCellById(id).executeAsOneOrNull())

        db.cellQueries.deleteCellById(id)

        assertNull(db.cellQueries.selectCellById(id).executeAsOneOrNull())
    }
}