package edu.gatech.cc.cellwatch.domain.fcc

import edu.gatech.cc.cellwatch.domain.model.CollectionMode
import edu.gatech.cc.cellwatch.domain.model.FccSubmission
import edu.gatech.cc.cellwatch.domain.model.Measurement
import edu.gatech.cc.cellwatch.domain.model.MeasurementGroup

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
)

data class MeasurementSequenceOutcome(
    val throughputServerMachine: String,
    val latencyServerMachine: String,
    val group: MeasurementGroup,
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

        val submission = buildSubmissionIfEligible(
            request = request,
            latencyMeasurement = latencyMeasurement,
            downloadMeasurement = downloadMeasurement,
            uploadMeasurement = uploadMeasurement,
        )
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
        )
    }

    private fun buildSubmissionIfEligible(
        request: MeasurementSequenceRequest,
        latencyMeasurement: Measurement,
        downloadMeasurement: Measurement,
        uploadMeasurement: Measurement,
    ): FccSubmission? {
        if (!FccSubmissionPolicy.shouldCreateSubmission(
                mode = request.mode,
                latencyMeasurement = latencyMeasurement,
                downloadMeasurement = downloadMeasurement,
                uploadMeasurement = uploadMeasurement,
            )
        ) {
            return null
        }

        val metadata = FccSubmissionPolicy.metadataSnapshot(
            latencyMeasurement = latencyMeasurement,
            downloadMeasurement = downloadMeasurement,
            uploadMeasurement = uploadMeasurement,
        )
        val context = submissionContextFactory.create(
            groupId = request.groupId,
            inVehicle = request.inVehicle,
            metadata = metadata,
        )
        return FccSubmissionPolicy.buildSubmission(context, metadata)
    }
}
