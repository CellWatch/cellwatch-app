package edu.gatech.cc.cellwatch.db

import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

object MeasurementQueriesContract {

    fun assertInsertAndSelectById(db: CellwatchDatabase) {
        val id = "measurement-1"
        val now = 1_710_000_000_000L

        db.measurementQueries.insertOrReplaceMeasurement(
            id = id,
            groupId = "group-1",
            campaignId = "campaign-1",
            sessionId = "session-1",
            deviceId = "device-1",
            deviceManufacturer = "Google",
            deviceModel = "Pixel",
            deviceOsName = "Android",
            deviceOsVersion = "14",
            appName = "CellWatch",
            provider = "carrier",
            type = "download",
            timestamp = now,
            duration = 1500L,
            scheduled = 1L,
            success = 1L,
            carrierAggregation = 0L,
            networkConnected = 1L,
            networkAvailable = 1L,
            networkRoaming = 0L,
            simMcc = "310",
            simMnc = "260",
            netMcc = "310",
            netMnc = "260",
            connectionType = "CELLULAR",
            cellularDataEnabled = 1L,
            extraData = "extra",
            createdOn = now,
            updatedOn = now,
            uploadTime = null,
            appVersion = "1.0.0",
        )

        val row = db.measurementQueries.selectMeasurementById(id).executeAsOneOrNull()
        assertNotNull(row)
        assertEquals("group-1", row.groupId)
        assertEquals("download", row.type)
        assertEquals("CELLULAR", row.connectionType)
    }

    fun assertSelectByGroupOrdersByTimestampDesc(db: CellwatchDatabase) {
        val groupId = "group-order"

        fun insert(id: String, timestamp: Long) {
            db.measurementQueries.insertOrReplaceMeasurement(
                id = id,
                groupId = groupId,
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
                timestamp = timestamp,
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

        insert("m1", 100L)
        insert("m2", 300L)
        insert("m3", 200L)

        val rows = db.measurementQueries.selectMeasurementsByGroupId(groupId).executeAsList()
        assertEquals(listOf("m2", "m3", "m1"), rows.map { it.id })
    }

    fun assertDeleteByIdRemovesRow(db: CellwatchDatabase) {
        val id = "measurement-delete"
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
            type = "upload",
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
        assertNotNull(db.measurementQueries.selectMeasurementById(id).executeAsOneOrNull())
        db.measurementQueries.deleteMeasurementById(id)
        assertNull(db.measurementQueries.selectMeasurementById(id).executeAsOneOrNull())
    }
}
