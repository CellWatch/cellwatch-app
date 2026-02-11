package edu.gatech.cc.cellwatch.domain.model

import com.benasher44.uuid.uuid4
import kotlinx.datetime.Clock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ChallengeDataTest {

    @Test
    fun createsChallengeDataWithDefaults() {
        val data = ChallengeData(submissionCategory = "TEST_CATEGORY")

        // UUID is auto-generated
        assertNotNull(data.id)
        // Submission category should be preserved
        assertEquals("TEST_CATEGORY", data.submissionCategory)
        // Optional fields should default to null
        assertEquals(null, data.contactName)
        assertEquals(null, data.createdOn)
    }

    @Test
    fun supportsCustomValues() {
        val now = Clock.System.now()
        val uuid = uuid4()
        val custom = ChallengeData(
            id = uuid,
            submissionCategory = "CUSTOM",
            contactName = "Ada Lovelace",
            dataSharingAcknowledgement = true,
            createdOn = now,
            updatedOn = now
        )

        assertEquals(uuid, custom.id)
        assertEquals("Ada Lovelace", custom.contactName)
        assertEquals(true, custom.dataSharingAcknowledgement)
        assertEquals(now, custom.createdOn)
    }
}