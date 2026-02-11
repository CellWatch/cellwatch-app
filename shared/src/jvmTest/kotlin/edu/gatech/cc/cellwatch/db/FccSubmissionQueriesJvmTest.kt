package edu.gatech.cc.cellwatch.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class FccSubmissionQueriesJvmTest {

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
    fun insert_and_select_by_id() = FccSubmissionQueriesContract.assertInsertAndSelectById(db)

    @Test
    fun select_unsubmitted_filters_submitted_rows() =
        FccSubmissionQueriesContract.assertSelectUnsubmittedFiltersSubmittedRows(db)

    @Test
    fun delete_by_id_removes_row() = FccSubmissionQueriesContract.assertDeleteByIdRemovesRow(db)
}
