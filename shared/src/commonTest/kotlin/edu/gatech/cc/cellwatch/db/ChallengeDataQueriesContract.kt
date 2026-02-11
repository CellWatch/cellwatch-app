package edu.gatech.cc.cellwatch.db

import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

object ChallengeDataQueriesContract {

    fun assertInsertAndSelectById(db: CellwatchDatabase) {
        val id = "challenge-1"
        val now = 1_710_000_000_000L

        db.challengeDataQueries.insertOrReplaceChallengeData(
            id = id,
            submissionCategory = "FCC",
            contactName = "Alice",
            contactEmail = "alice@example.com",
            contactPhone = "555",
            dataSharingAcknowledgement = 1L,
            createdOn = now,
            updatedOn = now,
        )

        val row = db.challengeDataQueries.selectChallengeDataById(id).executeAsOneOrNull()
        assertNotNull(row)
        assertEquals("FCC", row.submissionCategory)
        assertEquals(1L, row.dataSharingAcknowledgement)
    }

    fun assertSelectAllOrdersByCreatedOnDesc(db: CellwatchDatabase) {
        db.challengeDataQueries.insertOrReplaceChallengeData(
            id = "c1",
            submissionCategory = "A",
            contactName = null,
            contactEmail = null,
            contactPhone = null,
            dataSharingAcknowledgement = null,
            createdOn = 100L,
            updatedOn = null,
        )
        db.challengeDataQueries.insertOrReplaceChallengeData(
            id = "c2",
            submissionCategory = "B",
            contactName = null,
            contactEmail = null,
            contactPhone = null,
            dataSharingAcknowledgement = null,
            createdOn = 300L,
            updatedOn = null,
        )
        db.challengeDataQueries.insertOrReplaceChallengeData(
            id = "c3",
            submissionCategory = "C",
            contactName = null,
            contactEmail = null,
            contactPhone = null,
            dataSharingAcknowledgement = null,
            createdOn = 200L,
            updatedOn = null,
        )

        val rows = db.challengeDataQueries.selectAllChallengeData().executeAsList()
        assertEquals(listOf("c2", "c3", "c1"), rows.map { it.id })
    }

    fun assertDeleteByIdRemovesRow(db: CellwatchDatabase) {
        val id = "challenge-delete"
        db.challengeDataQueries.insertOrReplaceChallengeData(
            id = id,
            submissionCategory = "FCC",
            contactName = null,
            contactEmail = null,
            contactPhone = null,
            dataSharingAcknowledgement = null,
            createdOn = null,
            updatedOn = null,
        )
        assertNotNull(db.challengeDataQueries.selectChallengeDataById(id).executeAsOneOrNull())
        db.challengeDataQueries.deleteChallengeDataById(id)
        assertNull(db.challengeDataQueries.selectChallengeDataById(id).executeAsOneOrNull())
    }
}
