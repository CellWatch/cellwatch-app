package edu.gatech.cc.cellwatch.db

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.benasher44.uuid.uuid4
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class MeasurementDataQueriesIosTest {

    private lateinit var driver: NativeSqliteDriver
    private lateinit var db: CellwatchDatabase

    @BeforeTest
    fun setUp() {
        driver = NativeSqliteDriver(CellwatchDatabase.Schema, "measurement-data-ios-test-${uuid4()}.db")
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
