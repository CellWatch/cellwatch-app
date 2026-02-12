package edu.gatech.cc.cellwatch.domain.fcc

import edu.gatech.cc.cellwatch.domain.model.CollectionMode
import edu.gatech.cc.cellwatch.domain.model.FccSubmission
import edu.gatech.cc.cellwatch.domain.model.LatencyData
import edu.gatech.cc.cellwatch.domain.model.Measurement
import edu.gatech.cc.cellwatch.domain.model.NetworkConnectionType
import edu.gatech.cc.cellwatch.domain.model.UploadDownloadData
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
)

/**
 * Shared harness runner for Phase 3 orchestration wiring:
 * - uses platform MSAK server selection
 * - runs simulated latency/download/upload execution
 * - persists through an in-memory store adapter to validate orchestration flow
 */
class MeasurementSequenceHarness(
    private val config: MsakLocateConfig = MsakLocateConfig(
        environment = MsakLocateEnvironment.PROD,
        userAgent = "cellwatch-phase3-harness",
    ),
    private val clock: Clock = Clock.System,
) {
    constructor(config: MsakLocateConfig) : this(config = config, clock = Clock.System)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun runDefaultScenario(
        onComplete: (MeasurementSequenceHarnessResult?, Throwable?) -> Unit,
    ) {
        scope.launch {
            runCatching {
                val resultStore = InMemoryResultStore()
                val orchestrator = MeasurementSequenceOrchestrator(
                    serverPairProvider = SelectorBackedServerPairProvider(config),
                    measurementExecutor = SimulatedMeasurementExecutor(clock),
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
                MeasurementSequenceHarnessResult(
                    throughputMachine = outcome.throughputServerMachine,
                    latencyMachine = outcome.latencyServerMachine,
                    groupId = outcome.group.id,
                    submissionCreated = outcome.group.submission != null,
                    persistedMeasurements = resultStore.measurements.size,
                    persistedSubmissions = resultStore.submissions.size,
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

private class SelectorBackedServerPairProvider(
    private val config: MsakLocateConfig,
) : MsakServerPairProvider {
    override suspend fun chooseServers(): MsakServerPair {
        val locator = MsakServerSelectorPlatform.createLocator(config)
        val pinger = MsakServerSelectorPlatform.createPinger()
        return MsakServerSelector.chooseServers(locator, pinger)
    }
}

private class SimulatedMeasurementExecutor(
    private val clock: Clock,
) : MeasurementExecutor {
    override suspend fun runLatency(
        server: MsakServerEndpoint,
        groupId: String,
        measurementId: String?,
    ): Measurement {
        val id = measurementId ?: "latency-$groupId"
        return Measurement(
            id = id,
            groupId = groupId,
            type = "latency",
            timestamp = clock.now(),
            connectionType = NetworkConnectionType.CELLULAR,
            cellularDataEnabled = true,
            provider = server.machine,
            deviceManufacturer = "phase3-sim",
            deviceModel = "phase3-latency",
            deviceOsVersion = "14",
            appName = "CellWatch Shared",
            simMcc = "310",
            simMnc = "260",
            netMcc = "311",
            netMnc = "480",
            latencyData = LatencyData(
                id = "latency-data-$groupId",
                measurementId = id,
                rtt = 24,
                jitter = 2,
                sent = 10,
                received = 10,
                servers = listOf(server.machine),
            ),
        )
    }

    override suspend fun runThroughput(
        server: MsakServerEndpoint,
        direction: ThroughputDirection,
        groupId: String,
        measurementId: String?,
    ): Measurement {
        val prefix = if (direction == ThroughputDirection.DOWNLOAD) "download" else "upload"
        val id = measurementId?.let { "$prefix-$it" } ?: "$prefix-$groupId"
        return Measurement(
            id = id,
            groupId = groupId,
            type = prefix,
            timestamp = clock.now(),
            connectionType = NetworkConnectionType.CELLULAR,
            cellularDataEnabled = true,
            deviceId = if (direction == ThroughputDirection.DOWNLOAD) "phase3-device" else null,
            deviceModel = if (direction == ThroughputDirection.DOWNLOAD) "phase3-model" else null,
            appName = if (direction == ThroughputDirection.DOWNLOAD) "CellWatch Shared" else null,
            provider = server.machine,
            simMcc = if (direction == ThroughputDirection.DOWNLOAD) "310" else null,
            simMnc = if (direction == ThroughputDirection.DOWNLOAD) "260" else null,
            netMcc = if (direction == ThroughputDirection.DOWNLOAD) "311" else null,
            netMnc = if (direction == ThroughputDirection.DOWNLOAD) "480" else null,
            uploadDownloadData = UploadDownloadData(
                id = "ud-$id",
                measurementId = id,
                bytes = if (direction == ThroughputDirection.DOWNLOAD) 2_000_000 else 1_000_000,
                bytesPerSec = if (direction == ThroughputDirection.DOWNLOAD) 2_000_000.0 else 1_000_000.0,
                servers = listOf(server.machine),
            ),
        )
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
