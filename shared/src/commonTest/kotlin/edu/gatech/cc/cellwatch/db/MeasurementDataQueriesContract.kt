package edu.gatech.cc.cellwatch.db

import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

object MeasurementDataQueriesContract {

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

    fun assertLocationInsertSelectDelete(db: CellwatchDatabase) {
        val measurementId = "m-location"
        seedMeasurement(db, measurementId)

        db.locationQueries.insertOrReplaceLocation(
            id = "loc-1",
            timestamp = 100L,
            lat = 33.0,
            lon = -84.0,
            accuracy = 5.0,
            speed = 1.2,
            speedAccuracy = 0.4,
            heading = 45.0,
            measurementId = measurementId,
            createdOn = 100L,
            updatedOn = 100L,
        )

        val rows = db.locationQueries.selectLocationsByMeasurement(measurementId).executeAsList()
        assertEquals(1, rows.size)
        assertEquals("loc-1", rows.first().id)
        assertEquals(33.0, rows.first().lat)

        db.locationQueries.deleteLocationById("loc-1")
        assertNull(db.locationQueries.selectLocationById("loc-1").executeAsOneOrNull())
    }

    fun assertLatencyInsertSelectDeleteByMeasurement(db: CellwatchDatabase) {
        val measurementId = "m-latency"
        seedMeasurement(db, measurementId)

        db.latencyDataQueries.insertOrReplaceLatencyData(
            id = "lat-1",
            measurementId = measurementId,
            rtt = 10L,
            jitter = 2L,
            sent = 20L,
            received = 19L,
            servers = "[\"a.example.com\"]",
            createdOn = 100L,
            updatedOn = 100L,
        )

        val row = db.latencyDataQueries.selectLatencyDataById("lat-1").executeAsOneOrNull()
        assertNotNull(row)
        assertEquals(10L, row.rtt)
        assertEquals(measurementId, row.measurementId)

        db.latencyDataQueries.deleteLatencyDataByMeasurement(measurementId)
        assertNull(db.latencyDataQueries.selectLatencyDataById("lat-1").executeAsOneOrNull())
    }

    fun assertUploadInsertSelectDeleteByMeasurement(db: CellwatchDatabase) {
        val measurementId = "m-upload"
        seedMeasurement(db, measurementId)

        db.uploadDownloadDataQueries.insertOrReplaceUploadDownloadData(
            id = "ud-1",
            measurementId = measurementId,
            warmupDuration = 100L,
            warmupBytes = 200L,
            duration = 1000L,
            bytes = 2048L,
            bytesPerSec = 2048.0,
            applicationBytes = 1024L,
            applicationBytesPerSec = 1024.0,
            servers = "[\"b.example.com\"]",
            createdOn = 200L,
            updatedOn = 200L,
        )

        val row = db.uploadDownloadDataQueries.selectUploadDownloadDataById("ud-1").executeAsOneOrNull()
        assertNotNull(row)
        assertEquals(2048L, row.bytes)
        assertEquals(measurementId, row.measurementId)

        db.uploadDownloadDataQueries.deleteUploadDownloadDataByMeasurement(measurementId)
        assertNull(db.uploadDownloadDataQueries.selectUploadDownloadDataById("ud-1").executeAsOneOrNull())
    }
}
