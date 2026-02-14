package edu.gatech.cc.cellwatch.domain.measurementstart

import edu.gatech.cc.cellwatch.domain.model.CollectionMode

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
