package edu.gatech.cc.cellwatch.domain.measurementstart

import edu.gatech.cc.cellwatch.domain.model.CollectionMode

data class MeasurementStartPreflightFlowEvaluation(
    val state: MeasurementStartPreflightFlowUiState,
    val environment: MeasurementStartPreflightObservedEnvironment,
)

/**
 * Shared controller that keeps platform glue thin by composing:
 * - capability snapshot + override resolution
 * - flow view-model state transitions
 */
class MeasurementStartPreflightFlowController(
    private val environmentResolver: MeasurementStartPreflightEnvironmentResolver,
    private val flowViewModel: MeasurementStartPreflightFlowViewModel,
) {
    private var lastEnvironment = MeasurementStartPreflightObservedEnvironment(
        collectionMode = CollectionMode.FCC_CHALLENGE,
        hasRuntimeProfile = false,
        hasLocationPermission = false,
        networkPath = MeasurementNetworkPath.UNKNOWN,
    )

    fun currentState(): MeasurementStartPreflightFlowUiState = flowViewModel.currentState()

    fun setInVehicle(value: Boolean): MeasurementStartPreflightFlowUiState {
        return flowViewModel.setInVehicle(value)
    }

    fun onGoPressed(
        collectionMode: CollectionMode,
        capabilitySnapshot: MeasurementStartCapabilitySnapshot,
        overrides: MeasurementStartPreflightEnvironmentOverrides = MeasurementStartPreflightEnvironmentOverrides(),
    ): MeasurementStartPreflightFlowEvaluation {
        val resolved = environmentResolver.resolve(
            collectionMode = collectionMode,
            capabilitySnapshot = capabilitySnapshot,
            overrides = overrides,
        )
        lastEnvironment = resolved
        val state = flowViewModel.onGoPressed(environment = resolved)
        return MeasurementStartPreflightFlowEvaluation(
            state = state,
            environment = resolved,
        )
    }

    fun onConfirmProceed(): MeasurementStartPreflightFlowEvaluation {
        val state = flowViewModel.onConfirmProceed(environment = lastEnvironment)
        return MeasurementStartPreflightFlowEvaluation(
            state = state,
            environment = lastEnvironment,
        )
    }

    fun onConfirmCancel(): MeasurementStartPreflightFlowEvaluation {
        val state = flowViewModel.onConfirmCancel()
        return MeasurementStartPreflightFlowEvaluation(
            state = state,
            environment = lastEnvironment,
        )
    }
}
