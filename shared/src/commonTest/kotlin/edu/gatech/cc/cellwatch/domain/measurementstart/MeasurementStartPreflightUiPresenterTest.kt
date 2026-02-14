package edu.gatech.cc.cellwatch.domain.measurementstart

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MeasurementStartPreflightUiPresenterTest {
    private val presenter = MeasurementStartPreflightUiPresenter()

    @Test
    fun present_allowedResultShowsSuccess() {
        val presentation = presenter.present(
            MeasurementPreflightResult(
                allowed = true,
                reasonCode = MeasurementPreflightReasonCode.ALLOWED,
            ),
        )

        assertEquals("Preflight passed. You can start measuring.", presentation.statusMessage)
        assertFalse(presentation.statusIsError)
        assertNull(presentation.confirmationDialogMessage)
    }

    @Test
    fun present_missingPermissionShowsErrorMessage() {
        val presentation = presenter.present(
            MeasurementPreflightResult(
                allowed = false,
                reasonCode = MeasurementPreflightReasonCode.MISSING_LOCATION_PERMISSION,
            ),
        )

        assertEquals(
            "Location permission is required before starting measurement.",
            presentation.statusMessage,
        )
        assertTrue(presentation.statusIsError)
    }

    @Test
    fun present_challengeConfirmRequiredIncludesDialogMessage() {
        val presentation = presenter.present(
            MeasurementPreflightResult(
                allowed = false,
                reasonCode = MeasurementPreflightReasonCode.CHALLENGE_NON_CELLULAR_CONFIRM_REQUIRED,
                warningTextKey = "measurement.preflight.challenge_non_cellular_warning",
                requiresUserConfirm = true,
            ),
        )

        assertEquals("Wi-Fi detected. Choose Measure anyway or Cancel.", presentation.statusMessage)
        assertTrue(presentation.statusIsError)
        assertEquals(presenter.challengePathConfirmMessage(), presentation.confirmationDialogMessage)
    }

    @Test
    fun debugSummaryContainsStableFields() {
        val summary = presenter.debugSummary(
            result = MeasurementPreflightResult(
                allowed = false,
                reasonCode = MeasurementPreflightReasonCode.CHALLENGE_NON_CELLULAR_CONFIRM_REQUIRED,
                warningTextKey = "measurement.preflight.challenge_non_cellular_warning",
                requiresUserConfirm = true,
            ),
            networkPath = MeasurementNetworkPath.UNKNOWN,
            inVehicle = true,
        )

        assertTrue(summary.contains("allowed=false"))
        assertTrue(summary.contains("reason=CHALLENGE_NON_CELLULAR_CONFIRM_REQUIRED"))
        assertTrue(summary.contains("networkPath=UNKNOWN"))
        assertTrue(summary.contains("requiresUserConfirm=true"))
        assertTrue(summary.contains("warningKey=measurement.preflight.challenge_non_cellular_warning"))
        assertTrue(summary.contains("inVehicle=true"))
    }
}
