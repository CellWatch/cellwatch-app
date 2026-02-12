package edu.gatech.cc.cellwatch.domain.sync

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertNull

class SyncSmokeInvariantValidatorTest {

    private val validator = SyncSmokeInvariantValidator()

    @Test
    fun validateSuccess_whenAllExpectedSignalsPresent_returnsNull() {
        val error = validator.validateSuccess(
            measurementUploadPersisted = true,
            submissionUploadPersisted = true,
            remoteMeasurementVerified = true,
            measurementCompleteUploadTimeSet = true,
        )

        assertNull(error)
    }

    @Test
    fun validateSuccess_whenSignalMissing_returnsDescriptiveError() {
        val error = validator.validateSuccess(
            measurementUploadPersisted = true,
            submissionUploadPersisted = false,
            remoteMeasurementVerified = true,
            measurementCompleteUploadTimeSet = false,
        )

        assertContains(requireNotNull(error), "submissionUploadPersisted=false")
        assertContains(error, "measurementCompleteUploadTimeSet=false")
    }

    @Test
    fun validateFailure_whenErrorIsMissing_returnsError() {
        val error = validator.validateFailure("   ")
        assertContains(requireNotNull(error), "non-empty error message")
    }

    @Test
    fun validateFailure_whenErrorPresent_returnsNull() {
        assertNull(validator.validateFailure("synthetic smoke failure"))
    }
}
