package edu.gatech.cc.cellwatch.domain.fcc

import kotlin.test.Test
import kotlin.test.assertEquals

class FccSubmissionOutcomeMessageTest {

    @Test
    fun `a created submission is reported as submitted`() {
        assertEquals(
            FccSubmissionOutcomeMessage.SUBMITTED,
            FccSubmissionOutcomeMessage.forOutcome(submissionCreated = true, validation = null),
        )
    }

    @Test
    fun `opting out is named as the reason rather than the connection`() {
        // The eligibility text blames Wi-Fi. Telling a user who deliberately
        // turned submission off to check their connection is both wrong and
        // unactionable, so the opt-out is checked first.
        assertEquals(
            FccSubmissionOutcomeMessage.OPTED_OUT,
            FccSubmissionOutcomeMessage.forOutcome(
                submissionCreated = false,
                validation = null,
                challengeMode = false,
            ),
        )
    }

    @Test
    fun `challenge mode with no attempt falls back to eligibility`() {
        assertEquals(
            FccSubmissionOutcomeMessage.NOT_ELIGIBLE,
            FccSubmissionOutcomeMessage.forOutcome(
                submissionCreated = false,
                validation = null,
                challengeMode = true,
            ),
        )
    }

    @Test
    fun `a rejected submission still reports the validation reason`() {
        assertEquals(
            FccSubmissionOutcomeMessage.CARRIER_UNKNOWN,
            FccSubmissionOutcomeMessage.forOutcome(
                submissionCreated = false,
                validation = FccSubmissionValidationResult(
                    allowed = false,
                    codes = setOf(FccSubmissionValidationCode.MISSING_PROVIDER_NAME),
                ),
                challengeMode = true,
            ),
        )
    }
}
