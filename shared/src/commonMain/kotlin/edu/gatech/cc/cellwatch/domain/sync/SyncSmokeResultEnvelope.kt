package edu.gatech.cc.cellwatch.domain.sync

enum class SyncSmokeStatus {
    SUCCESS,
    FAILURE,
}

data class SyncSmokeResultEnvelope(
    val scenario: String,
    val status: SyncSmokeStatus,
    val message: String,
    val invariantSummary: String,
)

class SyncSmokeEnvelopeBuilder {
    private val validator: SyncSmokeInvariantValidator

    constructor() {
        validator = SyncSmokeInvariantValidator()
    }

    constructor(validator: SyncSmokeInvariantValidator) {
        this.validator = validator
    }

    fun mapStart(
        hasReport: Boolean,
        errorMessage: String?,
    ): SyncSmokeResultEnvelope {
        return if (hasReport) {
            SyncSmokeResultEnvelope(
                scenario = "map-start-sync",
                status = SyncSmokeStatus.SUCCESS,
                message = "map-start sync completed",
                invariantSummary = "report-present=true",
            )
        } else {
            failure("map-start-sync", errorMessage)
        }
    }

    fun measurementComplete(
        uploadTimeSet: Boolean,
        errorMessage: String?,
    ): SyncSmokeResultEnvelope {
        val invariant = if (uploadTimeSet) null else "measurementCompleteUploadTimeSet=false"
        return if (invariant == null) {
            SyncSmokeResultEnvelope(
                scenario = "measurement-complete-sync",
                status = SyncSmokeStatus.SUCCESS,
                message = "measurement-complete sync completed",
                invariantSummary = "measurementCompleteUploadTimeSet=true",
            )
        } else {
            SyncSmokeResultEnvelope(
                scenario = "measurement-complete-sync",
                status = SyncSmokeStatus.FAILURE,
                message = errorMessage ?: "measurement-complete sync failed",
                invariantSummary = invariant,
            )
        }
    }

    fun phase3Sequence(
        measurementCompleteUploadTimeSet: Boolean,
        persistedMeasurements: Int,
        persistedSubmissions: Int,
        errorMessage: String?,
    ): SyncSmokeResultEnvelope {
        val failures = mutableListOf<String>()
        if (!measurementCompleteUploadTimeSet) failures += "measurementCompleteUploadTimeSet=false"
        if (persistedMeasurements <= 0) failures += "persistedMeasurements<=0"
        if (persistedSubmissions <= 0) failures += "persistedSubmissions<=0"
        return if (failures.isEmpty()) {
            SyncSmokeResultEnvelope(
                scenario = "phase3-sequence-sync",
                status = SyncSmokeStatus.SUCCESS,
                message = "phase3 sequence + sync completed",
                invariantSummary = "measurementCompleteUploadTimeSet=true,persistedMeasurements>0,persistedSubmissions>0",
            )
        } else {
            SyncSmokeResultEnvelope(
                scenario = "phase3-sequence-sync",
                status = SyncSmokeStatus.FAILURE,
                message = errorMessage ?: "phase3 sequence + sync failed",
                invariantSummary = failures.joinToString(", "),
            )
        }
    }

    fun failure(
        scenario: String,
        errorMessage: String?,
    ): SyncSmokeResultEnvelope {
        val invariant = validator.validateFailure(errorMessage) ?: "error-message-present=true"
        return SyncSmokeResultEnvelope(
            scenario = scenario,
            status = SyncSmokeStatus.FAILURE,
            message = errorMessage ?: "operation failed",
            invariantSummary = invariant,
        )
    }
}

class SyncSmokeResultFormatter {
    fun format(envelope: SyncSmokeResultEnvelope): String {
        return buildString {
            appendLine("smokeEnvelope scenario=${envelope.scenario}")
            appendLine("status=${envelope.status.name}")
            appendLine("invariants=${envelope.invariantSummary}")
            append("message=${envelope.message}")
        }
    }
}
