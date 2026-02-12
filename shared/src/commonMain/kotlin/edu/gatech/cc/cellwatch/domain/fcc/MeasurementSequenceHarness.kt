package edu.gatech.cc.cellwatch.domain.fcc

import edu.gatech.cc.cellwatch.domain.capability.CapabilityCaptureReportFormatter
import edu.gatech.cc.cellwatch.domain.capability.NoOpPlatformCapabilityProvider
import edu.gatech.cc.cellwatch.domain.capability.PlatformCapabilityProvider
import edu.gatech.cc.cellwatch.domain.model.CollectionMode
import edu.gatech.cc.cellwatch.domain.model.FccSubmission
import edu.gatech.cc.cellwatch.domain.model.Measurement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

data class MeasurementSequenceHarnessResult(
    val throughputMachine: String,
    val latencyMachine: String,
    val groupId: String,
    val submissionCreated: Boolean,
    val persistedMeasurements: Int,
    val persistedSubmissions: Int,
    val persistedMeasurementsWithCapabilitySupport: Int,
    val persistedMeasurementsWithCapabilityNotes: Int,
    val capabilitySummary: String,
)

/**
 * Shared harness runner for Phase 3 orchestration wiring:
 * - uses platform MSAK server selection
 * - runs MSAK-backed latency/download/upload execution via platform adapters
 * - persists through an in-memory store adapter to validate orchestration flow
 */
class MeasurementSequenceHarness(
    private val config: MsakLocateConfig = MsakLocateConfig(
        environment = MsakLocateEnvironment.PROD,
        userAgent = "cellwatch-phase3-harness",
    ),
    private val clock: Clock = Clock.System,
    private val capabilityProvider: PlatformCapabilityProvider = NoOpPlatformCapabilityProvider,
) {
    constructor(config: MsakLocateConfig) : this(
        config = config,
        clock = Clock.System,
        capabilityProvider = NoOpPlatformCapabilityProvider,
    )
    constructor(
        config: MsakLocateConfig,
        capabilityProvider: PlatformCapabilityProvider,
    ) : this(
        config = config,
        clock = Clock.System,
        capabilityProvider = capabilityProvider,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun runDefaultScenario(
        onComplete: (MeasurementSequenceHarnessResult?, Throwable?) -> Unit,
    ) {
        scope.launch {
            runCatching {
                val capabilitySummary = runCatching {
                    CapabilityCaptureReportFormatter.format(
                        CapabilityCaptureReportFormatter.fromSnapshot(
                            capabilityProvider.captureSnapshot(),
                        ),
                    )
                }.getOrElse { error ->
                    "capabilities(capture=FAILED, error=${error.message})"
                }
                val resultStore = InMemoryResultStore()
                val orchestrator = MeasurementSequenceOrchestrator(
                    serverPairProvider = SelectorBackedServerPairProvider(config),
                    measurementExecutor = MsakMeasurementExecutorPlatform.create(
                        config = MsakMeasurementExecutorConfig(
                            userAgent = config.userAgent ?: "cellwatch-phase3-harness",
                            throughputStreams = 2,
                            throughputDurationMs = 5_000,
                            throughputDelayMs = 0,
                            latencyDurationMs = 3_000,
                            capabilityProvider = capabilityProvider,
                        ),
                        clock = clock,
                    ),
                    resultStore = resultStore,
                    submissionContextFactory = DefaultHarnessSubmissionContextFactory(clock),
                )
                val request = MeasurementSequenceRequest(
                    groupId = "phase3-${clock.now().toEpochMilliseconds()}",
                    inVehicle = false,
                    mode = CollectionMode.FCC_CHALLENGE,
                    measurementId = null,
                )
                val outcome = orchestrator.run(request)
                val persistedMeasurements = resultStore.measurements.size
                val withSupport = resultStore.measurements.count { it.hasCapabilitySupportStates() }
                val withNotes = resultStore.measurements.count { !it.capabilityNotes.isNullOrBlank() }
                MeasurementSequenceHarnessResult(
                    throughputMachine = outcome.throughputServerMachine,
                    latencyMachine = outcome.latencyServerMachine,
                    groupId = outcome.group.id,
                    submissionCreated = outcome.group.submission != null,
                    persistedMeasurements = persistedMeasurements,
                    persistedSubmissions = resultStore.submissions.size,
                    persistedMeasurementsWithCapabilitySupport = withSupport,
                    persistedMeasurementsWithCapabilityNotes = withNotes,
                    capabilitySummary = capabilitySummary,
                )
            }.onSuccess {
                onComplete(it, null)
            }.onFailure {
                onComplete(null, it)
            }
        }
    }

    fun close() {
        scope.cancel()
    }
}

private fun Measurement.hasCapabilitySupportStates(): Boolean {
    return !telephonySupport.isNullOrBlank() &&
        !networkSupport.isNullOrBlank() &&
        !locationSupport.isNullOrBlank() &&
        !deviceSupport.isNullOrBlank()
}

private class SelectorBackedServerPairProvider(
    private val config: MsakLocateConfig,
) : MsakServerPairProvider {
    override suspend fun chooseServers(): MsakServerPair {
        val locator = MsakServerSelectorPlatform.createLocator(config)
        val pinger = MsakServerSelectorPlatform.createPinger()
        return MsakServerSelector.chooseServers(locator, pinger)
    }
}

private class InMemoryResultStore : MeasurementResultStore {
    val measurements = mutableListOf<Measurement>()
    val submissions = mutableListOf<FccSubmission>()

    override suspend fun insertMeasurement(measurement: Measurement) {
        measurements += measurement
    }

    override suspend fun insertFccSubmission(submission: FccSubmission) {
        submissions += submission
    }
}

private class DefaultHarnessSubmissionContextFactory(
    private val clock: Clock,
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
            appVersion = "phase3-harness",
            provider = "phase3-harness",
            contactName = "Harness User",
            contactEmail = "harness@cellwatch.local",
            contactPhone = "555-0000",
        )
    }
}
