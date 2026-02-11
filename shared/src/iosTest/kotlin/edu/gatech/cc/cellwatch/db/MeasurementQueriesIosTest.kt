package edu.gatech.cc.cellwatch.db

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.benasher44.uuid.uuid4
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class MeasurementQueriesIosTest {

    private lateinit var driver: NativeSqliteDriver
    private lateinit var db: CellwatchDatabase

    @BeforeTest
    fun setUp() {
        driver = NativeSqliteDriver(CellwatchDatabase.Schema, "measurement-ios-test-${uuid4()}.db")
        db = CellwatchDatabase(driver)
    }

    @AfterTest
    fun tearDown() {
        if (this::driver.isInitialized) {
            driver.close()
        }
    }

    @Test
    fun insert_and_select_by_id() = MeasurementQueriesContract.assertInsertAndSelectById(db)

    @Test
    fun select_by_group_orders_by_timestamp_desc() =
        MeasurementQueriesContract.assertSelectByGroupOrdersByTimestampDesc(db)

    @Test
    fun delete_by_id_removes_row() = MeasurementQueriesContract.assertDeleteByIdRemovesRow(db)
}
