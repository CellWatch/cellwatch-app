package edu.gatech.cc.cellwatch.domain.measurementstart

import edu.gatech.cc.cellwatch.domain.model.CollectionMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MeasurementPreflightUseCaseMatrixTest {
    private val useCase = MeasurementPreflightUseCase()

    @Test
    fun matrix_runtimeProfileMissing_blocksBeforeOtherChecks() {
        val paths = listOf(
            MeasurementNetworkPath.CELLULAR,
            MeasurementNetworkPath.WIFI,
            MeasurementNetworkPath.UNKNOWN,
        )
        paths.forEach { path ->
            val result = evaluate(
                mode = CollectionMode.FCC_CHALLENGE,
                hasRuntimeProfile = false,
                hasLocationPermission = true,
                networkPath = path,
                confirmed = true,
            )
            assertFalse(result.allowed)
            assertEquals(MeasurementPreflightReasonCode.MISSING_RUNTIME_PROFILE, result.reasonCode)
            assertFalse(result.requiresUserConfirm)
        }
    }

    @Test
    fun matrix_locationMissing_blocksWhenRuntimeProfilePresent() {
        val result = evaluate(
            mode = CollectionMode.FCC_CHALLENGE,
            hasRuntimeProfile = true,
            hasLocationPermission = false,
            networkPath = MeasurementNetworkPath.CELLULAR,
            confirmed = true,
        )

        assertFalse(result.allowed)
        assertEquals(MeasurementPreflightReasonCode.MISSING_LOCATION_PERMISSION, result.reasonCode)
        assertFalse(result.requiresUserConfirm)
    }

    @Test
    fun matrix_fccChallenge_requiresConfirmOnNonCellularWithoutOverride() {
        listOf(MeasurementNetworkPath.WIFI, MeasurementNetworkPath.UNKNOWN).forEach { path ->
            val result = evaluate(
                mode = CollectionMode.FCC_CHALLENGE,
                hasRuntimeProfile = true,
                hasLocationPermission = true,
                networkPath = path,
                confirmed = false,
            )
            assertFalse(result.allowed)
            assertEquals(
                MeasurementPreflightReasonCode.CHALLENGE_NON_CELLULAR_CONFIRM_REQUIRED,
                result.reasonCode,
            )
            assertTrue(result.requiresUserConfirm)
            assertEquals("measurement.preflight.challenge_non_cellular_warning", result.warningTextKey)
        }
    }

    @Test
    fun matrix_fccChallenge_allowsCellularOrConfirmedNonCellular() {
        val cellular = evaluate(
            mode = CollectionMode.FCC_CHALLENGE,
            hasRuntimeProfile = true,
            hasLocationPermission = true,
            networkPath = MeasurementNetworkPath.CELLULAR,
            confirmed = false,
        )
        assertTrue(cellular.allowed)
        assertEquals(MeasurementPreflightReasonCode.ALLOWED, cellular.reasonCode)

        val wifiConfirmed = evaluate(
            mode = CollectionMode.FCC_CHALLENGE,
            hasRuntimeProfile = true,
            hasLocationPermission = true,
            networkPath = MeasurementNetworkPath.WIFI,
            confirmed = true,
        )
        assertTrue(wifiConfirmed.allowed)
        assertEquals(MeasurementPreflightReasonCode.ALLOWED, wifiConfirmed.reasonCode)
    }

    @Test
    fun matrix_testingMode_allowsRegardlessOfNetworkPath() {
        listOf(MeasurementNetworkPath.CELLULAR, MeasurementNetworkPath.WIFI, MeasurementNetworkPath.UNKNOWN)
            .forEach { path ->
                val result = evaluate(
                    mode = CollectionMode.TESTING,
                    hasRuntimeProfile = true,
                    hasLocationPermission = true,
                    networkPath = path,
                    confirmed = false,
                )
                assertTrue(result.allowed)
                assertEquals(MeasurementPreflightReasonCode.ALLOWED, result.reasonCode)
                assertFalse(result.requiresUserConfirm)
            }
    }

    private fun evaluate(
        mode: CollectionMode,
        hasRuntimeProfile: Boolean,
        hasLocationPermission: Boolean,
        networkPath: MeasurementNetworkPath,
        confirmed: Boolean,
    ): MeasurementPreflightResult {
        return useCase.evaluate(
            MeasurementStartPreflightInput(
                request = MeasurementStartRequest(
                    collectionMode = mode,
                    inVehicle = false,
                ),
                hasRuntimeProfile = hasRuntimeProfile,
                hasLocationPermission = hasLocationPermission,
                networkPath = networkPath,
                userConfirmedNonCellularChallengePath = confirmed,
            ),
        )
    }
}
