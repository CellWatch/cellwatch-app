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
class MeasurementQueriesTest {

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
    fun insert_and_select_by_id() = MeasurementQueriesContract.assertInsertAndSelectById(db)

    @Test
    fun select_by_group_orders_by_timestamp_desc() =
        MeasurementQueriesContract.assertSelectByGroupOrdersByTimestampDesc(db)

    @Test
    fun delete_by_id_removes_row() = MeasurementQueriesContract.assertDeleteByIdRemovesRow(db)

    @Test
    fun select_unsynced_filters_by_upload_time() =
        MeasurementQueriesContract.assertSelectUnsyncedFiltersByUploadTime(db)

    @Test
    fun mark_uploaded_sets_upload_time() =
        MeasurementQueriesContract.assertMarkUploadedSetsUploadTime(db)
}
