package edu.gatech.cc.cellwatch.domain.measurementstart

import edu.gatech.cc.cellwatch.domain.model.CollectionMode

enum class MeasurementNetworkPath {
    CELLULAR,
    WIFI,
    UNKNOWN,
}

enum class MeasurementPreflightReasonCode {
    ALLOWED,
    MISSING_RUNTIME_PROFILE,
    MISSING_LOCATION_PERMISSION,
    CHALLENGE_NON_CELLULAR_CONFIRM_REQUIRED,
}

data class MeasurementStartRequest(
    val collectionMode: CollectionMode,
    val inVehicle: Boolean,
)

data class MeasurementStartPreflightInput(
    val request: MeasurementStartRequest,
    val hasRuntimeProfile: Boolean,
    val hasLocationPermission: Boolean,
    val networkPath: MeasurementNetworkPath,
    val userConfirmedNonCellularChallengePath: Boolean,
)

data class MeasurementPreflightResult(
    val allowed: Boolean,
    val reasonCode: MeasurementPreflightReasonCode,
    val warningTextKey: String? = null,
    val requiresUserConfirm: Boolean = false,
)

class MeasurementPreflightUseCase {
    fun evaluate(input: MeasurementStartPreflightInput): MeasurementPreflightResult {
        if (!input.hasRuntimeProfile) {
            return MeasurementPreflightResult(
                allowed = false,
                reasonCode = MeasurementPreflightReasonCode.MISSING_RUNTIME_PROFILE,
            )
        }
        if (!input.hasLocationPermission) {
            return MeasurementPreflightResult(
                allowed = false,
                reasonCode = MeasurementPreflightReasonCode.MISSING_LOCATION_PERMISSION,
            )
        }
        if (input.request.collectionMode == CollectionMode.FCC_CHALLENGE &&
            input.networkPath != MeasurementNetworkPath.CELLULAR &&
            !input.userConfirmedNonCellularChallengePath
        ) {
            return MeasurementPreflightResult(
                allowed = false,
                reasonCode = MeasurementPreflightReasonCode.CHALLENGE_NON_CELLULAR_CONFIRM_REQUIRED,
                warningTextKey = "measurement.preflight.challenge_non_cellular_warning",
                requiresUserConfirm = true,
            )
        }
        return MeasurementPreflightResult(
            allowed = true,
            reasonCode = MeasurementPreflightReasonCode.ALLOWED,
        )
    }
}

data class MeasurementStartPreflightUiState(
    val inVehicle: Boolean = false,
    val userConfirmedNonCellularChallengePath: Boolean = false,
    val latestResult: MeasurementPreflightResult? = null,
)

class MeasurementStartPreflightViewModel(
    private val useCase: MeasurementPreflightUseCase,
) {
    private var state = MeasurementStartPreflightUiState()

    fun currentState(): MeasurementStartPreflightUiState = state

    fun setInVehicle(value: Boolean): MeasurementStartPreflightUiState {
        state = state.copy(inVehicle = value)
        return state
    }

    fun setChallengeNonCellularConfirmed(value: Boolean): MeasurementStartPreflightUiState {
        state = state.copy(userConfirmedNonCellularChallengePath = value)
        return state
    }

    fun evaluate(
        collectionMode: CollectionMode,
        hasRuntimeProfile: Boolean,
        hasLocationPermission: Boolean,
        networkPath: MeasurementNetworkPath,
    ): MeasurementPreflightResult {
        val result = useCase.evaluate(
            MeasurementStartPreflightInput(
                request = MeasurementStartRequest(
                    collectionMode = collectionMode,
                    inVehicle = state.inVehicle,
                ),
                hasRuntimeProfile = hasRuntimeProfile,
                hasLocationPermission = hasLocationPermission,
                networkPath = networkPath,
                userConfirmedNonCellularChallengePath = state.userConfirmedNonCellularChallengePath,
            ),
        )
        state = state.copy(latestResult = result)
        return result
    }
}
