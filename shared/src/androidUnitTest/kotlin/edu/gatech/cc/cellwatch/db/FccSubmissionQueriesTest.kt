package edu.gatech.cc.cellwatch.db

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FccSubmissionQueriesTest {

    private lateinit var driver: AndroidSqliteDriver
    private lateinit var db: CellwatchDatabase

    @Before
    fun setUp() {
        val ctx: Context = ApplicationProvider.getApplicationContext()
        driver = AndroidSqliteDriver(CellwatchDatabase.Schema, ctx, null)
        db = CellwatchDatabase(driver)
    }

    @After
    fun tearDown() {
        if (this::driver.isInitialized) driver.close()
    }

    @Test
    fun insert_and_select_by_id() = FccSubmissionQueriesContract.assertInsertAndSelectById(db)

    @Test
    fun select_unsubmitted_filters_submitted_rows() =
        FccSubmissionQueriesContract.assertSelectUnsubmittedFiltersSubmittedRows(db)

    @Test
    fun delete_by_id_removes_row() = FccSubmissionQueriesContract.assertDeleteByIdRemovesRow(db)
}
