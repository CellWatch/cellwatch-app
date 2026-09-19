package edu.gatech.cc.cellwatch.domain.measurementstart

import edu.gatech.cc.cellwatch.domain.model.CollectionMode

/**
 * Everything the start-measurement screen renders.
 *
 * [readyToRun] is the outcome the screen acts on: preflight passed, either
 * outright or after the user confirmed a warning, so navigation to the run can
 * proceed. It is deliberately separate from [statusMessage], which is only
 * text.
 */
data class MeasurementStartUiState(
    val inVehicle: Boolean,
    val statusMessage: String,
    val statusIsError: Boolean,
    val shouldPromptConfirmation: Boolean,
    val confirmationMessage: String?,
    val readyToRun: Boolean,
)

/**
 * The single entry point for the start-measurement screen (Rule 1).
 *
 * `measurementstart` held five classes: an environment resolver, a flow
 * controller wrapping a flow view model, a UI presenter, and a file named
 * `MeasurementStartPreflightViewModel` that actually contains the domain types
 * and the preflight policy rather than any presentation. A platform had to know
 * which to call and in what order; that orchestration is now here, once.
 *
 * Like map-home, this wraps rather than absorbs. The old classes stay public
 * because the harnesses construct them directly, and no logic moved, so their
 * tests still hold.
 *
 * Takes a capability snapshot from the caller rather than reading the platform
 * itself, per the contract: view models do not call platform APIs.
 */
class MeasurementStartViewModel(
    private val collectionMode: CollectionMode = CollectionMode.FCC_CHALLENGE,
) {
    private val controller = MeasurementStartPreflightFlowController(
        environmentResolver = MeasurementStartPreflightEnvironmentResolver(),
        flowViewModel = MeasurementStartPreflightFlowViewModel(
            useCase = MeasurementPreflightUseCase(),
            uiPresenter = MeasurementStartPreflightUiPresenter(),
        ),
    )

    fun currentState(): MeasurementStartUiState = controller.currentState().toUiState()

    fun setInVehicle(value: Boolean): MeasurementStartUiState =
        controller.setInVehicle(value).toUiState()

    /** Runs preflight against what the platform can currently see. */
    fun onStartPressed(snapshot: MeasurementStartCapabilitySnapshot): MeasurementStartUiState =
        controller.onGoPressed(
            collectionMode = collectionMode,
            capabilitySnapshot = snapshot,
        ).state.toUiState()

    /** The user accepted a warning - for example measuring off cellular. */
    fun onConfirmProceed(): MeasurementStartUiState =
        controller.onConfirmProceed().state.toUiState()

    fun onConfirmCancel(): MeasurementStartUiState =
        controller.onConfirmCancel().state.toUiState()

    private fun MeasurementStartPreflightFlowUiState.toUiState() = MeasurementStartUiState(
        inVehicle = inVehicle,
        statusMessage = statusMessage,
        statusIsError = statusIsError,
        shouldPromptConfirmation = shouldPromptConfirmation,
        confirmationMessage = confirmationMessage,
        // Ready only when preflight allowed it AND nothing is still being
        // asked of the user: a pending confirmation is not consent.
        readyToRun = latestResult?.allowed == true && !shouldPromptConfirmation,
    )
}
