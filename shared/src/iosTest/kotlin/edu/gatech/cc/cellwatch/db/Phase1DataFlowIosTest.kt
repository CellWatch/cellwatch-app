package edu.gatech.cc.cellwatch.db

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.benasher44.uuid.uuid4
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class Phase1DataFlowIosTest {

    private lateinit var driver: NativeSqliteDriver
    private lateinit var db: CellwatchDatabase

    @BeforeTest
    fun setUp() {
        driver = NativeSqliteDriver(CellwatchDatabase.Schema, "phase1-data-flow-${uuid4()}.db")
        driver.execute(null, "PRAGMA foreign_keys=ON", 0) {}
        db = CellwatchDatabase(driver)
    }

    @AfterTest
    fun tearDown() {
        if (this::driver.isInitialized) {
            driver.close()
        }
    }

    @Test
    fun measurement_group_lifecycle_round_trip() = runBlocking {
        Phase1DataFlowContract.assertMeasurementGroupLifecycle(db)
    }
}
