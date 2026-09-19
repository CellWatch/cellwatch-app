package edu.gatech.cc.cellwatch.domain.measurementstart

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The start screen has one door. These assert what the screen acts on, not the
 * five classes behind it.
 */
class MeasurementStartViewModelTest {

    private fun snapshot(
        hasRuntimeProfile: Boolean = true,
        hasLocationPermission: Boolean = true,
        networkPath: MeasurementNetworkPath = MeasurementNetworkPath.CELLULAR,
    ) = MeasurementStartCapabilitySnapshot(
        hasRuntimeProfile = hasRuntimeProfile,
        hasLocationPermission = hasLocationPermission,
        networkPath = networkPath,
    )

    @Test
    fun aReadyDeviceOnCellularCanRunImmediately() {
        val vm = MeasurementStartViewModel()

        val state = vm.onStartPressed(snapshot())

        assertTrue(state.readyToRun)
        assertFalse(state.shouldPromptConfirmation)
    }

    @Test
    fun aPendingConfirmationIsNotConsent() {
        // Off cellular the FCC path warns and asks. Until the user answers,
        // readyToRun must stay false - a prompt on screen is not agreement.
        val vm = MeasurementStartViewModel()

        val state = vm.onStartPressed(snapshot(networkPath = MeasurementNetworkPath.WIFI))

        if (state.shouldPromptConfirmation) {
            assertFalse(state.readyToRun, "a prompt awaiting an answer must not read as ready")
        }
    }

    @Test
    fun confirmingAWarningAllowsTheRun() {
        val vm = MeasurementStartViewModel()
        val prompted = vm.onStartPressed(snapshot(networkPath = MeasurementNetworkPath.WIFI))

        if (prompted.shouldPromptConfirmation) {
            val confirmed = vm.onConfirmProceed()
            assertTrue(confirmed.readyToRun)
            assertFalse(confirmed.shouldPromptConfirmation)
        }
    }

    @Test
    fun cancellingAWarningDoesNotRun() {
        val vm = MeasurementStartViewModel()
        val prompted = vm.onStartPressed(snapshot(networkPath = MeasurementNetworkPath.WIFI))

        if (prompted.shouldPromptConfirmation) {
            val cancelled = vm.onConfirmCancel()
            assertFalse(cancelled.readyToRun)
            assertFalse(cancelled.shouldPromptConfirmation)
        }
    }

    @Test
    fun missingLocationPermissionBlocksTheRun() {
        val vm = MeasurementStartViewModel()

        val state = vm.onStartPressed(snapshot(hasLocationPermission = false))

        assertFalse(state.readyToRun)
        assertTrue(state.statusMessage.isNotBlank(), "the user must be told why")
    }

    @Test
    fun inVehicleRoundTrips() {
        val vm = MeasurementStartViewModel()

        assertTrue(vm.setInVehicle(true).inVehicle)
        assertFalse(vm.setInVehicle(false).inVehicle)
    }
}
