package edu.gatech.cc.cellwatch.domain.measurementstart

data class MeasurementStartPreflightUiPresentation(
    val statusMessage: String,
    val statusIsError: Boolean,
    val confirmationDialogMessage: String? = null,
)

class MeasurementStartPreflightUiPresenter {
    fun present(result: MeasurementPreflightResult): MeasurementStartPreflightUiPresentation {
        val status = when {
            result.allowed -> MeasurementStartCopy.PREFLIGHT_PASSED
            result.reasonCode == MeasurementPreflightReasonCode.MISSING_LOCATION_PERMISSION ->
                MeasurementStartCopy.LOCATION_REQUIRED
            result.reasonCode == MeasurementPreflightReasonCode.MISSING_RUNTIME_PROFILE ->
                MeasurementStartCopy.COMPLETE_PROFILE
            result.reasonCode == MeasurementPreflightReasonCode.CHALLENGE_NON_CELLULAR_CONFIRM_REQUIRED ->
                MeasurementStartCopy.WIFI_DETECTED
            else -> MeasurementStartCopy.PREFLIGHT_BLOCKED
        }
        return MeasurementStartPreflightUiPresentation(
            statusMessage = status,
            statusIsError = !result.allowed,
            confirmationDialogMessage = if (result.requiresUserConfirm) challengePathConfirmMessage() else null,
        )
    }

    fun challengePathConfirmMessage(): String {
        return MeasurementStartCopy.WIFI_WARNING
    }

    fun debugSummary(
        result: MeasurementPreflightResult,
        networkPath: MeasurementNetworkPath,
        inVehicle: Boolean,
    ): String {
        return "allowed=${result.allowed};" +
            "reason=${result.reasonCode};" +
            "networkPath=$networkPath;" +
            "requiresUserConfirm=${result.requiresUserConfirm};" +
            "warningKey=${result.warningTextKey ?: "none"};" +
            "inVehicle=$inVehicle"
    }
}
