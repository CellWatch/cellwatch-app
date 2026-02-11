package edu.gatech.cc.cellwatch.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class Phase1DataFlowJvmTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var db: CellwatchDatabase

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        CellwatchDatabase.Schema.create(driver)
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
