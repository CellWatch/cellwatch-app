package edu.gatech.cc.cellwatch.domain.measurementstart

import edu.gatech.cc.cellwatch.domain.model.CollectionMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MeasurementStartPreflightFlowControllerTest {
    private val controller = MeasurementStartPreflightFlowController(
        environmentResolver = MeasurementStartPreflightEnvironmentResolver(),
        flowViewModel = MeasurementStartPreflightFlowViewModel(
            useCase = MeasurementPreflightUseCase(),
            uiPresenter = MeasurementStartPreflightUiPresenter(),
        ),
    )

    @Test
    fun scenarioMatrix_capabilitySnapshotsAndOverrides_resolvesDeterministically() {
        data class Case(
            val name: String,
            val mode: CollectionMode,
            val snapshot: MeasurementStartCapabilitySnapshot,
            val overrides: MeasurementStartPreflightEnvironmentOverrides,
            val expectedReason: MeasurementPreflightReasonCode,
            val expectedPrompt: Boolean,
        )

        val cases = listOf(
            Case(
                name = "runtime profile missing blocks first",
                mode = CollectionMode.FCC_CHALLENGE,
                snapshot = MeasurementStartCapabilitySnapshot(
                    hasRuntimeProfile = false,
                    hasLocationPermission = true,
                    networkPath = MeasurementNetworkPath.CELLULAR,
                ),
                overrides = MeasurementStartPreflightEnvironmentOverrides(),
                expectedReason = MeasurementPreflightReasonCode.MISSING_RUNTIME_PROFILE,
                expectedPrompt = false,
            ),
            Case(
                name = "location permission missing blocks",
                mode = CollectionMode.FCC_CHALLENGE,
                snapshot = MeasurementStartCapabilitySnapshot(
                    hasRuntimeProfile = true,
                    hasLocationPermission = false,
                    networkPath = MeasurementNetworkPath.CELLULAR,
                ),
                overrides = MeasurementStartPreflightEnvironmentOverrides(),
                expectedReason = MeasurementPreflightReasonCode.MISSING_LOCATION_PERMISSION,
                expectedPrompt = false,
            ),
            Case(
                name = "unknown path challenge prompts confirmation",
                mode = CollectionMode.FCC_CHALLENGE,
                snapshot = MeasurementStartCapabilitySnapshot(
                    hasRuntimeProfile = true,
                    hasLocationPermission = true,
                    networkPath = MeasurementNetworkPath.UNKNOWN,
                ),
                overrides = MeasurementStartPreflightEnvironmentOverrides(),
                expectedReason = MeasurementPreflightReasonCode.CHALLENGE_NON_CELLULAR_CONFIRM_REQUIRED,
                expectedPrompt = true,
            ),
            Case(
                name = "override network path can force cellular allow",
                mode = CollectionMode.FCC_CHALLENGE,
                snapshot = MeasurementStartCapabilitySnapshot(
                    hasRuntimeProfile = true,
                    hasLocationPermission = true,
                    networkPath = MeasurementNetworkPath.UNKNOWN,
                ),
                overrides = MeasurementStartPreflightEnvironmentOverrides(
                    networkPath = MeasurementNetworkPath.CELLULAR,
                ),
                expectedReason = MeasurementPreflightReasonCode.ALLOWED,
                expectedPrompt = false,
            ),
            Case(
                name = "testing mode allows non-cellular when requirements met",
                mode = CollectionMode.TESTING,
                snapshot = MeasurementStartCapabilitySnapshot(
                    hasRuntimeProfile = true,
                    hasLocationPermission = true,
                    networkPath = MeasurementNetworkPath.WIFI,
                ),
                overrides = MeasurementStartPreflightEnvironmentOverrides(),
                expectedReason = MeasurementPreflightReasonCode.ALLOWED,
                expectedPrompt = false,
            ),
            Case(
                name = "override location permission still blocks",
                mode = CollectionMode.FCC_CHALLENGE,
                snapshot = MeasurementStartCapabilitySnapshot(
                    hasRuntimeProfile = true,
                    hasLocationPermission = true,
                    networkPath = MeasurementNetworkPath.CELLULAR,
                ),
                overrides = MeasurementStartPreflightEnvironmentOverrides(
                    hasLocationPermission = false,
                ),
                expectedReason = MeasurementPreflightReasonCode.MISSING_LOCATION_PERMISSION,
                expectedPrompt = false,
            ),
        )

        cases.forEach { case ->
            controller.setInVehicle(false)
            val evaluation = controller.onGoPressed(
                collectionMode = case.mode,
                capabilitySnapshot = case.snapshot,
                overrides = case.overrides,
            )
            val result = evaluation.state.latestResult
            assertEquals(case.expectedReason, result?.reasonCode, case.name)
            assertEquals(case.expectedPrompt, evaluation.state.shouldPromptConfirmation, case.name)
        }
    }

    @Test
    fun onConfirmProceed_usesLastResolvedEnvironment() {
        val go = controller.onGoPressed(
            collectionMode = CollectionMode.FCC_CHALLENGE,
            capabilitySnapshot = MeasurementStartCapabilitySnapshot(
                hasRuntimeProfile = true,
                hasLocationPermission = true,
                networkPath = MeasurementNetworkPath.WIFI,
            ),
        )
        assertTrue(go.state.shouldPromptConfirmation)

        val confirmed = controller.onConfirmProceed()
        assertFalse(confirmed.state.shouldPromptConfirmation)
        assertFalse(confirmed.state.statusIsError)
        assertEquals(
            MeasurementPreflightReasonCode.ALLOWED,
            confirmed.state.latestResult?.reasonCode,
        )
    }
}
