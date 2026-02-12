package edu.gatech.cc.cellwatch.domain.fcc

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
                ),
                clock = clock,
            ),
            resultStore = resultStore,
            submissionContextFactory = FactoryHarnessSubmissionContextFactory(
                clock = clock,
                appSource = appSource,
            ),
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
            deviceType = "Android",
            deviceOsName = metadata.deviceOsVersion?.let { "Android $it" } ?: "Android",
            appVersion = appSource,
            provider = appSource,
            contactName = "Harness User",
            contactEmail = "harness@cellwatch.local",
            contactPhone = "555-0000",
        )
    }
}
