package edu.gatech.cc.cellwatch.domain.fcc

import edu.gatech.cc.cellwatch.domain.model.CollectionMode
import edu.gatech.cc.cellwatch.domain.model.Measurement
import edu.gatech.cc.cellwatch.domain.model.MeasurementGroup
import edu.gatech.cc.cellwatch.domain.sync.TcpTupleProvider
import edu.gatech.cc.cellwatch.domain.model.FccSubmission
import kotlin.coroutines.cancellation.CancellationException

enum class ThroughputDirection {
    DOWNLOAD,
    UPLOAD,
}

interface MsakServerPairProvider {
    suspend fun chooseServers(): MsakServerPair
}

interface MeasurementExecutor {
    suspend fun runLatency(
        server: MsakServerEndpoint,
        groupId: String,
        measurementId: String?,
    ): Measurement

    suspend fun runThroughput(
        server: MsakServerEndpoint,
        direction: ThroughputDirection,
        groupId: String,
        measurementId: String?,
    ): Measurement
}

interface MeasurementResultStore {
    suspend fun insertMeasurement(measurement: Measurement)
    suspend fun insertFccSubmission(submission: FccSubmission)
}

interface FccSubmissionContextFactory {
    fun create(
        request: MeasurementSequenceRequest,
        groupId: String,
        inVehicle: Boolean,
        metadata: FccSubmissionMetadataSnapshot,
    ): FccSubmissionBuildContext
}

data class MeasurementSequenceRequest(
    val groupId: String,
    val inVehicle: Boolean,
    val mode: CollectionMode,
    val measurementId: String? = null,
    val submissionProfile: FccSubmissionProfile? = null,
)

data class MeasurementSequenceOutcome(
    val throughputServerMachine: String,
    val latencyServerMachine: String,
    val group: MeasurementGroup,
    val submissionValidation: FccSubmissionValidationResult? = null,
)

enum class MeasurementSequenceStage {
    STARTED,
    LOCATE,
    LATENCY,
    DOWNLOAD,
    UPLOAD,
    DONE,
}

fun interface MeasurementSequenceProgressListener {
    fun onStageChanged(stage: MeasurementSequenceStage)
}

private object NoOpMeasurementSequenceProgressListener : MeasurementSequenceProgressListener {
    override fun onStageChanged(stage: MeasurementSequenceStage) = Unit
}

class MeasurementSequenceOrchestrator(
    private val serverPairProvider: MsakServerPairProvider,
    private val measurementExecutor: MeasurementExecutor,
    private val resultStore: MeasurementResultStore,
    private val submissionContextFactory: FccSubmissionContextFactory,
    private val progressListener: MeasurementSequenceProgressListener = NoOpMeasurementSequenceProgressListener,
    /**
     * Captures the device's public address while the measurement network is
     * still in use.
     *
     * Deliberately read here rather than at sync time. Sync can be deferred -
     * CellWatch queues submissions and retries later - so a measurement taken on
     * cellular and uploaded afterwards over WiFi would otherwise record the
     * WiFi-era address for a cellular measurement. Optional, because a failure to
     * reach the service must not cost us the measurement.
     */
    private val tcpTupleProvider: TcpTupleProvider? = null,
) {
    suspend fun run(request: MeasurementSequenceRequest): MeasurementSequenceOutcome {
        progressListener.onStageChanged(MeasurementSequenceStage.STARTED)
        progressListener.onStageChanged(MeasurementSequenceStage.LOCATE)
        val servers = serverPairProvider.chooseServers()

        progressListener.onStageChanged(MeasurementSequenceStage.LATENCY)
        val latencyMeasurement = measurementExecutor.runLatency(
            server = servers.latencyServer,
            groupId = request.groupId,
            measurementId = request.measurementId,
        )
        resultStore.insertMeasurement(latencyMeasurement)

        progressListener.onStageChanged(MeasurementSequenceStage.DOWNLOAD)
        val downloadMeasurement = measurementExecutor.runThroughput(
            server = servers.throughputServer,
            direction = ThroughputDirection.DOWNLOAD,
            groupId = request.groupId,
            measurementId = request.measurementId,
        )
        resultStore.insertMeasurement(downloadMeasurement)

        progressListener.onStageChanged(MeasurementSequenceStage.UPLOAD)
        val uploadMeasurement = measurementExecutor.runThroughput(
            server = servers.throughputServer,
            direction = ThroughputDirection.UPLOAD,
            groupId = request.groupId,
            measurementId = request.measurementId,
        )
        resultStore.insertMeasurement(uploadMeasurement)

        val submissionAttempt = buildSubmissionIfEligible(
            request = request,
            latencyMeasurement = latencyMeasurement,
            downloadMeasurement = downloadMeasurement,
            uploadMeasurement = uploadMeasurement,
        )
        val submission = submissionAttempt.submission
        if (submission != null) {
            resultStore.insertFccSubmission(submission)
        }
        progressListener.onStageChanged(MeasurementSequenceStage.DONE)

        return MeasurementSequenceOutcome(
            throughputServerMachine = servers.throughputServer.machine,
            latencyServerMachine = servers.latencyServer.machine,
            group = MeasurementGroup(
                latency = latencyMeasurement,
                download = downloadMeasurement,
                upload = uploadMeasurement,
                submission = submission,
            ),
            submissionValidation = submissionAttempt.validation,
        )
    }

    private suspend fun buildSubmissionIfEligible(
        request: MeasurementSequenceRequest,
        latencyMeasurement: Measurement,
        downloadMeasurement: Measurement,
        uploadMeasurement: Measurement,
    ): SubmissionAttempt {
        if (!FccSubmissionPolicy.shouldCreateSubmission(
                mode = request.mode,
                latencyMeasurement = latencyMeasurement,
                downloadMeasurement = downloadMeasurement,
                uploadMeasurement = uploadMeasurement,
            )
        ) {
            return SubmissionAttempt(
                submission = null,
                validation = null,
            )
        }

        val metadata = FccSubmissionPolicy.metadataSnapshot(
            latencyMeasurement = latencyMeasurement,
            downloadMeasurement = downloadMeasurement,
            uploadMeasurement = uploadMeasurement,
        )
        val context = submissionContextFactory.create(
            request = request,
            groupId = request.groupId,
            inVehicle = request.inVehicle,
            metadata = metadata,
        )
        val submission = FccSubmissionPolicy.buildSubmission(context, metadata)
            .withPublicTupleIfAvailable(tcpTupleProvider)
        val validation = FccSubmissionValidationPolicy.validate(
            submission = submission,
            latencyMeasurement = latencyMeasurement,
            downloadMeasurement = downloadMeasurement,
            uploadMeasurement = uploadMeasurement,
        )
        return SubmissionAttempt(
            submission = if (validation.allowed) submission else null,
            validation = validation,
        )
    }
}

/**
 * Fills sourceIp/sourcePort from the tuple service, leaving them null on any
 * failure so the Supabase `fcc_submission_update_source_ip` trigger can still
 * fall back to the address it observed. Returning a placeholder would defeat
 * that trigger, which only fires when the column is NULL.
 */
private suspend fun FccSubmission.withPublicTupleIfAvailable(
    provider: TcpTupleProvider?,
): FccSubmission {
    if (provider == null) return this
    val tuple = try {
        provider.getPublicTcpTuple()
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        return this
    }
    return copy(sourceIp = tuple.remoteAddress, sourcePort = tuple.remotePort)
}

private data class SubmissionAttempt(
    val submission: FccSubmission?,
    val validation: FccSubmissionValidationResult?,
)
