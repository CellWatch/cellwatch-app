package edu.gatech.cc.cellwatch.db

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.benasher44.uuid.uuid4
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class FccSubmissionQueriesIosTest {

    private lateinit var driver: NativeSqliteDriver
    private lateinit var db: CellwatchDatabase

    @BeforeTest
    fun setUp() {
        driver = NativeSqliteDriver(CellwatchDatabase.Schema, "fcc-ios-test-${uuid4()}.db")
        db = CellwatchDatabase(driver)
    }

    @AfterTest
    fun tearDown() {
        if (this::driver.isInitialized) {
            driver.close()
        }
    }

    @Test
    fun insert_and_select_by_id() = FccSubmissionQueriesContract.assertInsertAndSelectById(db)

    @Test
    fun select_unsubmitted_filters_submitted_rows() =
        FccSubmissionQueriesContract.assertSelectUnsubmittedFiltersSubmittedRows(db)

    @Test
    fun delete_by_id_removes_row() = FccSubmissionQueriesContract.assertDeleteByIdRemovesRow(db)
}
