package edu.gatech.cc.cellwatch.domain.measurementstart

import edu.gatech.cc.cellwatch.domain.model.CollectionMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MeasurementStartPreflightFlowViewModelTest {
    private val viewModel = MeasurementStartPreflightFlowViewModel(
        useCase = MeasurementPreflightUseCase(),
        uiPresenter = MeasurementStartPreflightUiPresenter(),
    )

    @Test
    fun onGoPressed_nonCellularChallenge_promptsConfirmation() {
        val state = viewModel.onGoPressed(
            MeasurementStartPreflightObservedEnvironment(
                collectionMode = CollectionMode.FCC_CHALLENGE,
                hasRuntimeProfile = true,
                hasLocationPermission = true,
                networkPath = MeasurementNetworkPath.WIFI,
            ),
        )

        assertTrue(state.shouldPromptConfirmation)
        assertTrue(state.statusIsError)
        assertEquals("Wi-Fi detected. Choose Measure anyway or Cancel.", state.statusMessage)
        assertTrue(state.debugSummary.contains("requiresUserConfirm=true"))
    }

    @Test
    fun onConfirmProceed_afterPrompt_allowsMeasurement() {
        viewModel.onGoPressed(
            MeasurementStartPreflightObservedEnvironment(
                collectionMode = CollectionMode.FCC_CHALLENGE,
                hasRuntimeProfile = true,
                hasLocationPermission = true,
                networkPath = MeasurementNetworkPath.UNKNOWN,
            ),
        )

        val confirmed = viewModel.onConfirmProceed(
            MeasurementStartPreflightObservedEnvironment(
                collectionMode = CollectionMode.FCC_CHALLENGE,
                hasRuntimeProfile = true,
                hasLocationPermission = true,
                networkPath = MeasurementNetworkPath.UNKNOWN,
            ),
        )

        assertFalse(confirmed.shouldPromptConfirmation)
        assertFalse(confirmed.statusIsError)
        assertEquals("Preflight passed. You can start measuring.", confirmed.statusMessage)
        assertTrue(confirmed.debugSummary.contains("allowed=true"))
    }

    @Test
    fun onConfirmCancel_hidesPrompt_andKeepsBlockedStatus() {
        viewModel.onGoPressed(
            MeasurementStartPreflightObservedEnvironment(
                collectionMode = CollectionMode.FCC_CHALLENGE,
                hasRuntimeProfile = true,
                hasLocationPermission = true,
                networkPath = MeasurementNetworkPath.WIFI,
            ),
        )

        val canceled = viewModel.onConfirmCancel()

        assertFalse(canceled.shouldPromptConfirmation)
        assertTrue(canceled.statusIsError)
        assertEquals("Wi-Fi detected. Choose Measure anyway or Cancel.", canceled.statusMessage)
    }
}
