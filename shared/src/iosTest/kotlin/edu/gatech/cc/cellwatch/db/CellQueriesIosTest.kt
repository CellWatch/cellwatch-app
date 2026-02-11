package edu.gatech.cc.cellwatch.db

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.benasher44.uuid.uuid4
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class CellQueriesIosTest {

    private lateinit var driver: NativeSqliteDriver
    private lateinit var db: CellwatchDatabase

    @BeforeTest
    fun setUp() {
        driver = NativeSqliteDriver(CellwatchDatabase.Schema, "cell-ios-test-${uuid4()}.db")
        db = CellwatchDatabase(driver)

        driver.execute(null, "DELETE FROM CellEntity", 0) {}
        driver.execute(null, "DELETE FROM MeasurementEntity", 0) {}
    }

    @AfterTest
    fun tearDown() {
        if (this::driver.isInitialized) {
            driver.close()
        }
    }

    @Test
    fun insert_and_select_by_id() = CellQueriesContract.assertInsertAndSelectById(db)

    @Test
    fun select_by_measurement_orders_by_timestamp_desc() =
        CellQueriesContract.assertSelectByMeasurementOrdersByTimestampDesc(db)

    @Test
    fun delete_by_id_removes_row() = CellQueriesContract.assertDeleteByIdRemovesRow(db)
}
