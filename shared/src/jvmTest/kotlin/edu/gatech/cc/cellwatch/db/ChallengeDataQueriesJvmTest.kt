package edu.gatech.cc.cellwatch.db

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class ChallengeDataQueriesJvmTest {

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
    fun insert_and_select_by_id() = ChallengeDataQueriesContract.assertInsertAndSelectById(db)

    @Test
    fun select_all_orders_by_created_on_desc() =
        ChallengeDataQueriesContract.assertSelectAllOrdersByCreatedOnDesc(db)

    @Test
    fun delete_by_id_removes_row() = ChallengeDataQueriesContract.assertDeleteByIdRemovesRow(db)
}
