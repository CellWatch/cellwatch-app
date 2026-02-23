package edu.gatech.cc.cellwatch.domain.measurementstart

import edu.gatech.cc.cellwatch.domain.model.CollectionMode
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

object MeasurementStartPreflightParityContract {
    fun assertChallengeConfirmPathAndPresenterText() {
        val useCase = MeasurementPreflightUseCase()
        val presenter = MeasurementStartPreflightUiPresenter()

        val blocked = useCase.evaluate(
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
        assertFalse(blocked.allowed)
        assertEquals(MeasurementPreflightReasonCode.CHALLENGE_NON_CELLULAR_CONFIRM_REQUIRED, blocked.reasonCode)
        assertTrue(blocked.requiresUserConfirm)
        val blockedUi = presenter.present(blocked)
        assertEquals("Wi-Fi detected. Choose Measure anyway or Cancel.", blockedUi.statusMessage)
        assertTrue(blockedUi.statusIsError)

        val allowed = useCase.evaluate(
            MeasurementStartPreflightInput(
                request = MeasurementStartRequest(
                    collectionMode = CollectionMode.FCC_CHALLENGE,
                    inVehicle = false,
                ),
                hasRuntimeProfile = true,
                hasLocationPermission = true,
                networkPath = MeasurementNetworkPath.WIFI,
                userConfirmedNonCellularChallengePath = true,
            ),
        )
        assertTrue(allowed.allowed)
        assertEquals(MeasurementPreflightReasonCode.ALLOWED, allowed.reasonCode)
        val allowedUi = presenter.present(allowed)
        assertEquals("Preflight passed. You can start measuring.", allowedUi.statusMessage)
        assertFalse(allowedUi.statusIsError)
    }
}
