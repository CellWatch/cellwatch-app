package edu.gatech.cc.cellwatch.data.mappers

import edu.gatech.cc.cellwatch.domain.model.FccSubmission
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class FccSubmissionMappersTest {

    @Test
    fun round_trip_preserves_submission_flags() {
        val now = Instant.fromEpochMilliseconds(1_710_000_000_000L)
        val submission = FccSubmission(
            id = "s-1",
            challengeDataId = "c-1",
            sourcePort = 443,
            inVehicle = true,
            externalAntenna = false,
            submitted = true,
            createdOn = now,
            updatedOn = now,
            uploadTime = now,
        )

        val back = submission.toRow().toDomain()

        assertEquals(submission.id, back.id)
        assertEquals(submission.challengeDataId, back.challengeDataId)
        assertEquals(submission.sourcePort, back.sourcePort)
        assertEquals(submission.inVehicle, back.inVehicle)
        assertEquals(submission.externalAntenna, back.externalAntenna)
        assertEquals(submission.submitted, back.submitted)
        assertEquals(submission.uploadTime, back.uploadTime)
    }
}
