package edu.gatech.cc.cellwatch.db

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class Phase1DataFlowTest {

    private lateinit var driver: AndroidSqliteDriver
    private lateinit var db: CellwatchDatabase

    @Before
    fun setUp() {
        val ctx: Context = ApplicationProvider.getApplicationContext()
        driver = AndroidSqliteDriver(CellwatchDatabase.Schema, ctx, null)
        driver.execute(null, "PRAGMA foreign_keys=ON", 0) {}
        db = CellwatchDatabase(driver)
    }

    @After
    fun tearDown() {
        if (this::driver.isInitialized) driver.close()
    }

    @Test
    fun measurement_group_lifecycle_round_trip() = runBlocking {
        Phase1DataFlowContract.assertMeasurementGroupLifecycle(db)
    }
}
