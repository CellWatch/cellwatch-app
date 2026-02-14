package edu.gatech.cc.cellwatch.domain.fcc

import edu.gatech.cc.cellwatch.domain.capability.NoOpPlatformCapabilityProvider
import edu.gatech.cc.cellwatch.domain.capability.PlatformCapabilityProvider
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Builds the default MSAK-backed measurement sequence orchestrator used by harness apps.
 */
object DefaultMsakMeasurementSequenceOrchestratorFactory {
    fun create(
        config: MsakLocateConfig,
        resultStore: MeasurementResultStore,
        clock: Clock = Clock.System,
        appSource: String = "phase3-harness",
        capabilityProvider: PlatformCapabilityProvider = NoOpPlatformCapabilityProvider,
        progressListener: MeasurementSequenceProgressListener = MeasurementSequenceProgressListener { _ -> },
    ): MeasurementSequenceOrchestrator {
        return MeasurementSequenceOrchestrator(
            serverPairProvider = FactorySelectorBackedServerPairProvider(config),
            measurementExecutor = MsakMeasurementExecutorPlatform.create(
                config = MsakMeasurementExecutorConfig(
                    userAgent = config.userAgent ?: appSource,
                    throughputStreams = 2,
                    throughputDurationMs = 5_000,
                    throughputDelayMs = 0,
                    latencyDurationMs = 3_000,
                    capabilityProvider = capabilityProvider,
                ),
                clock = clock,
            ),
            resultStore = resultStore,
            submissionContextFactory = FactoryHarnessSubmissionContextFactory(
                clock = clock,
                appSource = appSource,
            ),
            progressListener = progressListener,
        )
    }
}

private class FactorySelectorBackedServerPairProvider(
    private val config: MsakLocateConfig,
) : MsakServerPairProvider {
    override suspend fun chooseServers(): MsakServerPair {
        val locator = MsakServerSelectorPlatform.createLocator(config)
        val pinger = MsakServerSelectorPlatform.createPinger()
        return MsakServerSelector.chooseServers(locator, pinger)
    }
}

private class FactoryHarnessSubmissionContextFactory(
    private val clock: Clock,
    private val appSource: String,
) : FccSubmissionContextFactory {
    override fun create(
        groupId: String,
        inVehicle: Boolean,
        metadata: FccSubmissionMetadataSnapshot,
    ): FccSubmissionBuildContext {
        return FccSubmissionBuildContext(
            groupId = groupId,
            deviceTimestamp = Instant.fromEpochMilliseconds(clock.now().toEpochMilliseconds()),
            inVehicle = inVehicle,
            externalAntenna = false,
            deviceType = inferDeviceType(metadata.deviceOsName),
            deviceOsName = formatDeviceOsName(metadata.deviceOsName, metadata.deviceOsVersion),
            appVersion = appSource,
            provider = appSource,
            contactName = "Harness User",
            contactEmail = "harness@cellwatch.local",
            contactPhone = "555-0000",
        )
    }
}

internal fun inferDeviceType(deviceOsName: String?): String {
    val normalized = deviceOsName?.trim()?.lowercase()
    return when {
        normalized == null -> "Unknown"
        normalized.contains("ios") || normalized.contains("iphone") -> "iOS"
        normalized.contains("android") -> "Android"
        else -> deviceOsName
    }
}

internal fun formatDeviceOsName(
    deviceOsName: String?,
    deviceOsVersion: String?,
): String? {
    val name = deviceOsName?.trim()?.takeIf { it.isNotEmpty() }
    val version = deviceOsVersion?.trim()?.takeIf { it.isNotEmpty() }
    return when {
        name != null && version != null -> "$name $version"
        name != null -> name
        version != null -> version
        else -> null
    }
}
