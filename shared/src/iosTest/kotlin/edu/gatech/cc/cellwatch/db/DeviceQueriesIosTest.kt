package edu.gatech.cc.cellwatch.db

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DeviceQueriesIosTest {

    private lateinit var driver: NativeSqliteDriver
    private lateinit var db: CellwatchDatabase

    @BeforeTest
    fun setUp() {
        // Always create a driver and DB instance for this test run.
        // Schema.create() is invoked by NativeSqliteDriver, so all tables
        // including DeviceEntity are created here if they do not exist.
        driver = NativeSqliteDriver(CellwatchDatabase.Schema, ":memory:")
        db = CellwatchDatabase(driver)

        // Ensure we start from a clean slate even if the underlying
        // in-memory DB connection is being reused by the runtime.
        // If the table is already empty, this is a no-op.
        driver.execute(
            null,
            "DELETE FROM DeviceEntity",
            0
        ) {
            // no bindings
        }
    }

    @AfterTest
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
        assertEquals(id, row.deviceId)
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