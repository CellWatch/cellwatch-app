package edu.gatech.cc.cellwatch.domain.fcc

/**
 * Why a completed measurement will or will not reach the FCC.
 *
 * A measurement can finish perfectly and still be withheld - taken off
 * cellular, contact details missing, no carrier detected. Nothing surfaced
 * that: the run reported "Measurement complete" and the submission was
 * silently dropped, so a user could collect measurements for an afternoon
 * believing they were contributing to a challenge and submit none of them.
 *
 * Phrased as what to do about it where there is something to do.
 */
object FccSubmissionOutcomeMessage {

    const val SUBMITTED = "This measurement will be submitted to the FCC."

    const val OPTED_OUT =
        "Not submitted to the FCC: FCC submission is turned off in Settings. This measurement " +
            "is still saved and uploaded to the CellWatch server."

    const val NOT_ELIGIBLE =
        "Not submitted to the FCC: the FCC only accepts measurements taken over a cellular " +
            "connection. Turn off Wi-Fi and measure again."

    const val CONTACT_INCOMPLETE =
        "Not submitted to the FCC: your contact details are incomplete. Add your name, email " +
            "and phone in your profile, then measure again."

    const val CARRIER_UNKNOWN =
        "Not submitted to the FCC: no mobile carrier was detected for this measurement."

    const val DEVICE_INCOMPLETE =
        "Not submitted to the FCC: this device did not report the information the FCC requires."

    /**
     * For records written after the fact.
     *
     * The validation result exists only while a run is in flight and is not
     * stored, so an export cannot always say why a submission was withheld.
     * Saying so is better than reusing a run-time message that asserts a
     * specific cause - an export may end up in front of the FCC, and a
     * confident wrong reason is worse there than an admitted gap.
     */
    const val REASON_UNRECORDED =
        "No FCC submission was created for this run. The specific reason was not recorded " +
            "with the measurement."

    const val INCOMPLETE_TESTS =
        "Not submitted to the FCC: one of the three tests did not produce a result."

    /**
     * [validation] is null when no submission was attempted at all, which the
     * orchestrator does when the measurement was not eligible in the first
     * place - a different thing from one that was built and then rejected.
     */
    fun forOutcome(
        submissionCreated: Boolean,
        validation: FccSubmissionValidationResult?,
        challengeMode: Boolean = true,
    ): String {
        if (submissionCreated) return SUBMITTED
        // Checked before the eligibility branches: a user who turned
        // submission off would otherwise be told their connection was the
        // problem, which is both wrong and unactionable.
        if (!challengeMode) return OPTED_OUT
        val codes = validation?.codes ?: return NOT_ELIGIBLE
        return when {
            codes.any { it in MEASUREMENT_CODES } -> INCOMPLETE_TESTS
            codes.any { it in CONTACT_CODES } -> CONTACT_INCOMPLETE
            FccSubmissionValidationCode.MISSING_PROVIDER_NAME in codes -> CARRIER_UNKNOWN
            else -> DEVICE_INCOMPLETE
        }
    }

    private val MEASUREMENT_CODES = setOf(
        FccSubmissionValidationCode.MISSING_LATENCY_MEASUREMENT,
        FccSubmissionValidationCode.MISSING_DOWNLOAD_MEASUREMENT,
        FccSubmissionValidationCode.MISSING_UPLOAD_MEASUREMENT,
    )

    private val CONTACT_CODES = setOf(
        FccSubmissionValidationCode.MISSING_CONTACT_NAME,
        FccSubmissionValidationCode.MISSING_CONTACT_EMAIL,
        FccSubmissionValidationCode.INVALID_CONTACT_EMAIL,
        FccSubmissionValidationCode.MISSING_CONTACT_PHONE,
        FccSubmissionValidationCode.INVALID_CONTACT_PHONE,
    )
}
