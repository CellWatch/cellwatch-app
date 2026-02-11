package edu.gatech.cc.cellwatch.db

import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

object DeviceQueriesContract {

    fun assertInsertAndSelectById(db: CellwatchDatabase) {
        val id = "dev-1"

        db.deviceQueries.insertDevice(
            deviceId = id,
            deviceManufacturer = "Google",
            deviceModel = "Pixel 9",
            deviceOsName = "Android",
            deviceOsVersion = "14",
            appName = "CellWatch"
        )

        val row = db.deviceQueries.getDeviceById(id).executeAsOneOrNull()
        assertNotNull(row)
        assertEquals(id, row.deviceId)
        assertEquals("Google", row.deviceManufacturer)
        assertEquals("Pixel 9", row.deviceModel)
        assertEquals("Android", row.deviceOsName)
        assertEquals("14", row.deviceOsVersion)
        assertEquals("CellWatch", row.appName)
    }

    fun assertSelectAllReturnsAllInsertedDevices(db: CellwatchDatabase) {
        db.deviceQueries.insertDevice(
            deviceId = "dev-1",
            deviceManufacturer = "Google",
            deviceModel = "Pixel 9",
            deviceOsName = "Android",
            deviceOsVersion = "14",
            appName = "CellWatch"
        )
        db.deviceQueries.insertDevice(
            deviceId = "dev-2",
            deviceManufacturer = "Apple",
            deviceModel = "iPhone 16",
            deviceOsName = "iOS",
            deviceOsVersion = "18",
            appName = "CellWatch"
        )
        db.deviceQueries.insertDevice(
            deviceId = "dev-3",
            deviceManufacturer = "Samsung",
            deviceModel = "Galaxy S25",
            deviceOsName = "Android",
            deviceOsVersion = "15",
            appName = "CellWatch"
        )

        val rows = db.deviceQueries.selectAllDevices().executeAsList()
        val ids = rows.map { it.deviceId }.toSet()

        assertEquals(3, rows.size)
        assertEquals(setOf("dev-1", "dev-2", "dev-3"), ids)
    }

    fun assertDeleteByIdRemovesRow(db: CellwatchDatabase) {
        val id = "dev-delete"

        db.deviceQueries.insertDevice(
            deviceId = id,
            deviceManufacturer = null,
            deviceModel = null,
            deviceOsName = null,
            deviceOsVersion = null,
            appName = null
        )

        assertNotNull(db.deviceQueries.getDeviceById(id).executeAsOneOrNull())
        db.deviceQueries.deleteDeviceById(id)
        assertNull(db.deviceQueries.getDeviceById(id).executeAsOneOrNull())
    }
}
