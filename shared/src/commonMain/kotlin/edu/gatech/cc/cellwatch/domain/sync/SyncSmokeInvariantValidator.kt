package edu.gatech.cc.cellwatch.domain.sync

class SyncSmokeInvariantValidator {

    fun validateSuccess(
        measurementUploadPersisted: Boolean,
        submissionUploadPersisted: Boolean,
        remoteMeasurementVerified: Boolean,
        measurementCompleteUploadTimeSet: Boolean,
    ): String? {
        val failures = mutableListOf<String>()
        if (!measurementCompleteUploadTimeSet) failures += "measurementCompleteUploadTimeSet=false"
        if (!measurementUploadPersisted) failures += "measurementUploadPersisted=false"
        if (!submissionUploadPersisted) failures += "submissionUploadPersisted=false"
        if (!remoteMeasurementVerified) failures += "remoteMeasurementVerified=false"
        return if (failures.isEmpty()) null else failures.joinToString(separator = ", ")
    }

    fun validateFailure(errorMessage: String?): String? {
        return if (errorMessage.isNullOrBlank()) {
            "expected non-empty error message"
        } else {
            null
        }
    }
}
