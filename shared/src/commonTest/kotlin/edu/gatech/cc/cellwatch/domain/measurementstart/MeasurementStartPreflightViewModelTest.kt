package edu.gatech.cc.cellwatch.domain.measurementstart

import edu.gatech.cc.cellwatch.domain.model.CollectionMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MeasurementStartPreflightViewModelTest {
    private val useCase = MeasurementPreflightUseCase()

    @Test
    fun preflight_blocksWhenRuntimeProfileMissing() {
        val result = useCase.evaluate(
            MeasurementStartPreflightInput(
                request = MeasurementStartRequest(
                    collectionMode = CollectionMode.FCC_CHALLENGE,
                    inVehicle = false,
                ),
                hasRuntimeProfile = false,
                hasLocationPermission = true,
                networkPath = MeasurementNetworkPath.CELLULAR,
                userConfirmedNonCellularChallengePath = true,
            ),
        )

        assertFalse(result.allowed)
        assertEquals(MeasurementPreflightReasonCode.MISSING_RUNTIME_PROFILE, result.reasonCode)
    }

    @Test
    fun preflight_blocksWhenLocationPermissionMissing() {
        val result = useCase.evaluate(
            MeasurementStartPreflightInput(
                request = MeasurementStartRequest(
                    collectionMode = CollectionMode.FCC_CHALLENGE,
                    inVehicle = true,
                ),
                hasRuntimeProfile = true,
                hasLocationPermission = false,
                networkPath = MeasurementNetworkPath.CELLULAR,
                userConfirmedNonCellularChallengePath = true,
            ),
        )

        assertFalse(result.allowed)
        assertEquals(MeasurementPreflightReasonCode.MISSING_LOCATION_PERMISSION, result.reasonCode)
    }

    @Test
    fun preflight_requiresConfirmForChallengeOnNonCellularPath() {
        val result = useCase.evaluate(
            MeasurementStartPreflightInput(
                request = MeasurementStartRequest(
                    collectionMode = CollectionMode.FCC_CHALLENGE,
                    inVehicle = false,
                ),
                hasRuntimeProfile = true,
                hasLocationPermission = true,
                networkPath = MeasurementNetworkPath.WIFI,
                userConfirmedNonCellularChallengePath = false,
            ),
        )

        assertFalse(result.allowed)
        assertEquals(MeasurementPreflightReasonCode.CHALLENGE_NON_CELLULAR_CONFIRM_REQUIRED, result.reasonCode)
        assertTrue(result.requiresUserConfirm)
        assertEquals("measurement.preflight.challenge_non_cellular_warning", result.warningTextKey)
    }

    @Test
    fun viewModel_allowsAfterChallengeConfirm() {
        val viewModel = MeasurementStartPreflightViewModel(useCase)
        viewModel.setInVehicle(true)
        viewModel.setChallengeNonCellularConfirmed(true)

        val result = viewModel.evaluate(
            collectionMode = CollectionMode.FCC_CHALLENGE,
            hasRuntimeProfile = true,
            hasLocationPermission = true,
            networkPath = MeasurementNetworkPath.WIFI,
        )

        assertTrue(result.allowed)
        assertEquals(MeasurementPreflightReasonCode.ALLOWED, result.reasonCode)
        assertTrue(viewModel.currentState().inVehicle)
        assertTrue(viewModel.currentState().userConfirmedNonCellularChallengePath)
        assertEquals(MeasurementPreflightReasonCode.ALLOWED, viewModel.currentState().latestResult?.reasonCode)
    }
}
