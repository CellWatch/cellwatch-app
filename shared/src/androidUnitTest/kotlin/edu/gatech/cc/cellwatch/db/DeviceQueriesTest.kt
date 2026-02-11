package edu.gatech.cc.cellwatch.db

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DeviceQueriesTest {

    private lateinit var driver: AndroidSqliteDriver
    private lateinit var db: CellwatchDatabase

    @Before
    fun setUp() {
        val ctx: Context = ApplicationProvider.getApplicationContext()
        // null name -> in-memory DB
        driver = AndroidSqliteDriver(CellwatchDatabase.Schema, ctx, null)
        db = CellwatchDatabase(driver)
    }

    @After
    fun tearDown() {
        if (this::driver.isInitialized) {
            driver.close()
        }
    }

    @Test
    fun insert_and_select_by_id() {
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
        assertEquals(id, row!!.deviceId)
        assertEquals("Google", row.deviceManufacturer)
        assertEquals("Pixel 9", row.deviceModel)
        assertEquals("Android", row.deviceOsName)
        assertEquals("14", row.deviceOsVersion)
        assertEquals("CellWatch", row.appName)
    }

    @Test
    fun select_all_returns_all_inserted_devices() {
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

    @Test
    fun delete_by_id_removes_row() {
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