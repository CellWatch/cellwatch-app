package edu.gatech.cc.cellwatch.domain.measurementstart

data class MeasurementStartPreflightFlowUiState(
    val inVehicle: Boolean = false,
    val latestResult: MeasurementPreflightResult? = null,
    val statusMessage: String = "Tap Go to check readiness.",
    val statusIsError: Boolean = false,
    val debugSummary: String = "",
    val shouldPromptConfirmation: Boolean = false,
    val confirmationMessage: String? = null,
)

class MeasurementStartPreflightFlowViewModel(
    private val useCase: MeasurementPreflightUseCase,
    private val uiPresenter: MeasurementStartPreflightUiPresenter,
) {
    private var state = MeasurementStartPreflightFlowUiState()

    fun currentState(): MeasurementStartPreflightFlowUiState = state

    fun setInVehicle(value: Boolean): MeasurementStartPreflightFlowUiState {
        state = state.copy(inVehicle = value)
        return state
    }

    fun onGoPressed(environment: MeasurementStartPreflightObservedEnvironment): MeasurementStartPreflightFlowUiState {
        return evaluate(environment = environment, userConfirmedNonCellularPath = false)
    }

    fun onConfirmProceed(environment: MeasurementStartPreflightObservedEnvironment): MeasurementStartPreflightFlowUiState {
        return evaluate(environment = environment, userConfirmedNonCellularPath = true)
    }

    fun onConfirmCancel(): MeasurementStartPreflightFlowUiState {
        state = state.copy(
            shouldPromptConfirmation = false,
            confirmationMessage = null,
        )
        return state
    }

    private fun evaluate(
        environment: MeasurementStartPreflightObservedEnvironment,
        userConfirmedNonCellularPath: Boolean,
    ): MeasurementStartPreflightFlowUiState {
        val result = useCase.evaluate(
            MeasurementStartPreflightInput(
                request = MeasurementStartRequest(
                    collectionMode = environment.collectionMode,
                    inVehicle = state.inVehicle,
                ),
                hasRuntimeProfile = environment.hasRuntimeProfile,
                hasLocationPermission = environment.hasLocationPermission,
                networkPath = environment.networkPath,
                userConfirmedNonCellularChallengePath = userConfirmedNonCellularPath,
            ),
        )
        val presentation = uiPresenter.present(result)
        state = state.copy(
            latestResult = result,
            statusMessage = presentation.statusMessage,
            statusIsError = presentation.statusIsError,
            debugSummary = uiPresenter.debugSummary(
                result = result,
                networkPath = environment.networkPath,
                inVehicle = state.inVehicle,
            ),
            shouldPromptConfirmation = result.requiresUserConfirm,
            confirmationMessage = presentation.confirmationDialogMessage,
        )
        return state
    }
}
