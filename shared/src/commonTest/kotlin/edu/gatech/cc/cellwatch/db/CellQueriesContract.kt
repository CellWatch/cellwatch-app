package edu.gatech.cc.cellwatch.db

import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

object CellQueriesContract {

    private fun seedMeasurement(db: CellwatchDatabase, id: String) {
        db.measurementQueries.insertOrReplaceMeasurement(
            id = id,
            groupId = null,
            campaignId = null,
            sessionId = null,
            deviceId = null,
            deviceManufacturer = null,
            deviceModel = null,
            deviceOsName = null,
            deviceOsVersion = null,
            appName = null,
            provider = null,
            type = "latency",
            timestamp = null,
            duration = null,
            scheduled = null,
            success = null,
            carrierAggregation = null,
            networkConnected = null,
            networkAvailable = null,
            networkRoaming = null,
            simMcc = null,
            simMnc = null,
            netMcc = null,
            netMnc = null,
            connectionType = null,
            cellularDataEnabled = null,
            extraData = null,
            createdOn = null,
            updatedOn = null,
            uploadTime = null,
            appVersion = null,
        )
    }

    fun assertInsertAndSelectById(db: CellwatchDatabase) {
        val id = "cell-1"
        val now = 1_710_000_000L
        seedMeasurement(db, "m-1")

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
        assertEquals(id, row.id)
        assertEquals(now, row.timestamp)
        assertEquals("n78", row.spectrumBand)
    }

    fun assertSelectByMeasurementOrdersByTimestampDesc(db: CellwatchDatabase) {
        val mId = "measure-xyz"
        seedMeasurement(db, mId)

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

    fun assertDeleteByIdRemovesRow(db: CellwatchDatabase) {
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
