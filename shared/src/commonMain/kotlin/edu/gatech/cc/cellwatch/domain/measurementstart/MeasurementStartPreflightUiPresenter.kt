package edu.gatech.cc.cellwatch.domain.measurementstart

data class MeasurementStartPreflightUiPresentation(
    val statusMessage: String,
    val statusIsError: Boolean,
    val confirmationDialogMessage: String? = null,
)

class MeasurementStartPreflightUiPresenter {
    fun present(result: MeasurementPreflightResult): MeasurementStartPreflightUiPresentation {
        val status = when {
            result.allowed -> "Preflight passed. You can start measuring."
            result.reasonCode == MeasurementPreflightReasonCode.MISSING_LOCATION_PERMISSION ->
                "Location permission is required before starting measurement."
            result.reasonCode == MeasurementPreflightReasonCode.MISSING_RUNTIME_PROFILE ->
                "Complete profile setup before starting measurement."
            result.reasonCode == MeasurementPreflightReasonCode.CHALLENGE_NON_CELLULAR_CONFIRM_REQUIRED ->
                "Wi-Fi detected. Choose Measure anyway or Cancel."
            else -> "Preflight blocked. Review requirements and try again."
        }
        return MeasurementStartPreflightUiPresentation(
            statusMessage = status,
            statusIsError = !result.allowed,
            confirmationDialogMessage = if (result.requiresUserConfirm) challengePathConfirmMessage() else null,
        )
    }

    fun challengePathConfirmMessage(): String {
        return "It looks like you are connected to Wi-Fi or network path is unknown. " +
            "If you proceed, your measurement may not be submitted to the FCC."
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
