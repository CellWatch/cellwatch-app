package edu.gatech.cc.cellwatch.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class MeasurementDataQueriesJvmTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var db: CellwatchDatabase

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        CellwatchDatabase.Schema.create(driver)
        db = CellwatchDatabase(driver)
    }

    @AfterTest
    fun tearDown() {
        if (this::driver.isInitialized) {
            driver.close()
        }
    }

    @Test
    fun location_insert_select_delete() = MeasurementDataQueriesContract.assertLocationInsertSelectDelete(db)

    @Test
    fun latency_insert_select_delete_by_measurement() =
        MeasurementDataQueriesContract.assertLatencyInsertSelectDeleteByMeasurement(db)

    @Test
    fun upload_insert_select_delete_by_measurement() =
        MeasurementDataQueriesContract.assertUploadInsertSelectDeleteByMeasurement(db)
}
