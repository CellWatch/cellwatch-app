package edu.gatech.cc.cellwatch.domain.measurementstart

import edu.gatech.cc.cellwatch.domain.model.CollectionMode

data class MeasurementStartCapabilitySnapshot(
    val hasRuntimeProfile: Boolean,
    val hasLocationPermission: Boolean,
    val networkPath: MeasurementNetworkPath,
)

data class MeasurementStartPreflightObservedEnvironment(
    val collectionMode: CollectionMode,
    val hasRuntimeProfile: Boolean,
    val hasLocationPermission: Boolean,
    val networkPath: MeasurementNetworkPath,
)

data class MeasurementStartPreflightEnvironmentOverrides(
    val collectionMode: CollectionMode? = null,
    val hasRuntimeProfile: Boolean? = null,
    val hasLocationPermission: Boolean? = null,
    val networkPath: MeasurementNetworkPath? = null,
)

class MeasurementStartPreflightEnvironmentResolver {
    fun observedEnvironment(
        collectionMode: CollectionMode,
        capabilitySnapshot: MeasurementStartCapabilitySnapshot,
    ): MeasurementStartPreflightObservedEnvironment {
        return MeasurementStartPreflightObservedEnvironment(
            collectionMode = collectionMode,
            hasRuntimeProfile = capabilitySnapshot.hasRuntimeProfile,
            hasLocationPermission = capabilitySnapshot.hasLocationPermission,
            networkPath = capabilitySnapshot.networkPath,
        )
    }

    fun resolve(
        collectionMode: CollectionMode,
        capabilitySnapshot: MeasurementStartCapabilitySnapshot,
        overrides: MeasurementStartPreflightEnvironmentOverrides = MeasurementStartPreflightEnvironmentOverrides(),
    ): MeasurementStartPreflightObservedEnvironment {
        return resolve(observedEnvironment(collectionMode, capabilitySnapshot), overrides)
    }

    fun resolve(
        observed: MeasurementStartPreflightObservedEnvironment,
        overrides: MeasurementStartPreflightEnvironmentOverrides = MeasurementStartPreflightEnvironmentOverrides(),
    ): MeasurementStartPreflightObservedEnvironment {
        return MeasurementStartPreflightObservedEnvironment(
            collectionMode = overrides.collectionMode ?: observed.collectionMode,
            hasRuntimeProfile = overrides.hasRuntimeProfile ?: observed.hasRuntimeProfile,
            hasLocationPermission = overrides.hasLocationPermission ?: observed.hasLocationPermission,
            networkPath = overrides.networkPath ?: observed.networkPath,
        )
    }
}
