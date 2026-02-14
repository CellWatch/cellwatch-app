package edu.gatech.cc.cellwatch.domain.measurementstart

import edu.gatech.cc.cellwatch.domain.model.CollectionMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MeasurementStartPreflightEnvironmentResolverTest {
    private val resolver = MeasurementStartPreflightEnvironmentResolver()

    @Test
    fun resolve_withoutOverrides_returnsObservedValues() {
        val observed = MeasurementStartPreflightObservedEnvironment(
            collectionMode = CollectionMode.TESTING,
            hasRuntimeProfile = false,
            hasLocationPermission = true,
            networkPath = MeasurementNetworkPath.CELLULAR,
        )

        val resolved = resolver.resolve(observed)

        assertEquals(CollectionMode.TESTING, resolved.collectionMode)
        assertFalse(resolved.hasRuntimeProfile)
        assertTrue(resolved.hasLocationPermission)
        assertEquals(MeasurementNetworkPath.CELLULAR, resolved.networkPath)
    }

    @Test
    fun resolve_withOverrides_prefersOverrideValues() {
        val observed = MeasurementStartPreflightObservedEnvironment(
            collectionMode = CollectionMode.FCC_CHALLENGE,
            hasRuntimeProfile = true,
            hasLocationPermission = true,
            networkPath = MeasurementNetworkPath.CELLULAR,
        )
        val overrides = MeasurementStartPreflightEnvironmentOverrides(
            collectionMode = CollectionMode.TESTING,
            hasRuntimeProfile = false,
            hasLocationPermission = false,
            networkPath = MeasurementNetworkPath.UNKNOWN,
        )

        val resolved = resolver.resolve(observed = observed, overrides = overrides)

        assertEquals(CollectionMode.TESTING, resolved.collectionMode)
        assertFalse(resolved.hasRuntimeProfile)
        assertFalse(resolved.hasLocationPermission)
        assertEquals(MeasurementNetworkPath.UNKNOWN, resolved.networkPath)
    }
}
