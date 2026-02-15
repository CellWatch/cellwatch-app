package edu.gatech.cc.cellwatch.domain.sync

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.benasher44.uuid.uuid4
import edu.gatech.cc.cellwatch.data.remote.DeviceAuthStore
import edu.gatech.cc.cellwatch.data.sync.DefaultSyncRemoteDataSourceFactory
import edu.gatech.cc.cellwatch.data.sync.MeasurementSyncServiceFactory
import edu.gatech.cc.cellwatch.data.sync.SyncRemoteProfile
import edu.gatech.cc.cellwatch.data.sync.SyncRuntimeConfigFactory
import edu.gatech.cc.cellwatch.data.sync.SyncTransportTarget
import edu.gatech.cc.cellwatch.data.sync.SupabaseSyncRemoteDataSourceProvider
import edu.gatech.cc.cellwatch.db.CellwatchDatabase
import edu.gatech.cc.cellwatch.domain.capability.CapabilityCaptureReportFormatter
import edu.gatech.cc.cellwatch.domain.capability.CapabilityPersistenceSummaryFormatter
import edu.gatech.cc.cellwatch.domain.capability.IosPlatformCapabilityProvider
import edu.gatech.cc.cellwatch.domain.fcc.DefaultMsakMeasurementSequenceOrchestratorFactory
import edu.gatech.cc.cellwatch.domain.fcc.MeasurementSequenceRequest
import edu.gatech.cc.cellwatch.domain.fcc.MeasurementSequenceStage
import edu.gatech.cc.cellwatch.domain.fcc.MsakLocateConfig
import edu.gatech.cc.cellwatch.domain.fcc.RepositoryBackedMeasurementResultStore
import edu.gatech.cc.cellwatch.domain.model.CollectionMode
import edu.gatech.cc.cellwatch.domain.model.TcpTuple
import edu.gatech.cc.cellwatch.domain.measurementrun.MeasurementRunUiPresenter
import edu.gatech.cc.cellwatch.domain.measurementrun.MeasurementRunViewController
import edu.gatech.cc.cellwatch.domain.measurementrun.MeasurementRunProgress
import edu.gatech.cc.cellwatch.domain.measurementrun.MeasurementRunState
import edu.gatech.cc.cellwatch.domain.measurementrun.MeasurementResultReadModelUseCase
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.Throws
import kotlin.coroutines.EmptyCoroutineContext
import edu.gatech.cc.cellwatch.domain.sync.renderForStatus

data class IosPhase3SequenceSyncResult(
    val groupId: String,
    val throughputMachine: String,
    val latencyMachine: String,
    val submissionCreated: Boolean,
    val mapStartMeasurementsUploaded: Int,
    val mapStartSubmissionsUploaded: Int,
    val measurementCompleteUploadTimeSet: Boolean,
    val measurementCompleteReportSummary: String,
    val persistedMeasurements: Int,
    val persistedSubmissions: Int,
    val capabilityPersistenceSummary: String,
    val capabilitySummary: String,
    val latencySummary: String,
    val downloadSummary: String,
    val uploadSummary: String,
    val completionSummary: String,
)

class IosPhase3SequenceSyncHarness {
    fun runAsync(
        msakConfig: MsakLocateConfig,
        supabaseUrl: String,
        supabaseApiKey: String,
        onProgressHeader: ((String) -> Unit)? = null,
        onComplete: (IosPhase3SequenceSyncResult?, Throwable?) -> Unit,
    ) {
        val handler = CoroutineExceptionHandler { _, throwable ->
            onComplete(null, throwable)
        }
        CoroutineScope(SupervisorJob() + Dispatchers.Default + handler).launch {
            runCatching {
                run(
                    msakConfig = msakConfig,
                    supabaseUrl = supabaseUrl,
                    supabaseApiKey = supabaseApiKey,
                    onProgressHeader = onProgressHeader,
                )
            }.onSuccess { result ->
                onComplete(result, null)
            }.onFailure { error ->
                onComplete(null, error)
            }
        }
    }

    @Throws(Exception::class)
    suspend fun run(
        msakConfig: MsakLocateConfig,
        supabaseUrl: String,
        supabaseApiKey: String,
        onProgressHeader: ((String) -> Unit)? = null,
    ): IosPhase3SequenceSyncResult {
        val now = Clock.System.now()
        val runController = MeasurementRunViewController()
        val runPresenter = MeasurementRunUiPresenter()
        val deviceAuthStore = SequenceHarnessDeviceAuthStore()
        val driver = NativeSqliteDriver(
            schema = CellwatchDatabase.Schema,
            name = "ios-phase3-sequence-sync-${uuid4()}.db",
        )
        val db = CellwatchDatabase(driver)
        val measurementRepo = edu.gatech.cc.cellwatch.data.repo.MeasurementRepositoryImpl(
            queries = db.measurementQueries,
            io = EmptyCoroutineContext,
        )
        val latencyRepo = edu.gatech.cc.cellwatch.data.repo.LatencyDataRepositoryImpl(
            queries = db.latencyDataQueries,
            io = EmptyCoroutineContext,
        )
        val uploadDownloadRepo = edu.gatech.cc.cellwatch.data.repo.UploadDownloadDataRepositoryImpl(
            queries = db.uploadDownloadDataQueries,
            io = EmptyCoroutineContext,
        )
        val submissionRepo = edu.gatech.cc.cellwatch.data.repo.FccSubmissionRepositoryImpl(
            queries = db.fccSubmissionQueries,
            io = EmptyCoroutineContext,
        )

        try {
            val capabilityProvider = IosPlatformCapabilityProvider(
                clock = object : Clock {
                    override fun now(): Instant = now
                },
            )
            val remoteProfile = SyncRemoteProfile.Supabase(
                configResolver = SyncRuntimeConfigFactory.fromRaw(
                    allowRemote = false,
                    localUrl = supabaseUrl,
                    localApiKey = supabaseApiKey,
                ),
                target = SyncTransportTarget.LOCAL,
            )
            val uploadTriggerUseCase = MeasurementSyncServiceFactory.createUploadTriggerUseCase(
                database = db,
                io = EmptyCoroutineContext,
                remoteProfile = remoteProfile,
                remoteFactory = DefaultSyncRemoteDataSourceFactory(
                    SupabaseSyncRemoteDataSourceProvider(deviceAuthStore = deviceAuthStore),
                ),
                tcpTupleProvider = object : TcpTupleProvider {
                    override suspend fun getPublicTcpTuple(): TcpTuple {
                        return TcpTuple(
                            remoteAddress = "203.0.113.11",
                            remotePort = 4242,
                            timestamp = now.toEpochMilliseconds(),
                        )
                    }
                },
                clock = object : Clock {
                    override fun now(): Instant = now
                },
            )
            val request = MeasurementSequenceRequest(
                groupId = uuid4().toString(),
                inVehicle = false,
                mode = CollectionMode.FCC_CHALLENGE,
                measurementId = null,
            )
            val sequenceOrchestrator = DefaultMsakMeasurementSequenceOrchestratorFactory.create(
                config = msakConfig,
                resultStore = RepositoryBackedMeasurementResultStore(
                    measurementRepository = measurementRepo,
                    latencyDataRepository = latencyRepo,
                    uploadDownloadDataRepository = uploadDownloadRepo,
                    submissionRepository = submissionRepo,
                ),
                clock = object : Clock {
                    override fun now(): Instant = now
                },
                appSource = "ios-test-app-phase3-sync",
                capabilityProvider = capabilityProvider,
                progressListener = { stage ->
                    when (stage) {
                        MeasurementSequenceStage.STARTED -> runController.onSequenceStarted(request.groupId)
                        MeasurementSequenceStage.LOCATE -> runController.onLocateStarted()
                        MeasurementSequenceStage.LATENCY -> runController.onLatencyStarted()
                        MeasurementSequenceStage.DOWNLOAD -> runController.onDownloadStarted()
                        MeasurementSequenceStage.UPLOAD -> runController.onUploadStarted()
                        MeasurementSequenceStage.DONE -> Unit
                    }
                    onProgressHeader?.invoke(runPresenter.present(runController.currentState()).headerText)
                },
            )
            val capabilitySummary = runCatching {
                CapabilityCaptureReportFormatter.format(
                    CapabilityCaptureReportFormatter.fromSnapshot(
                        capabilityProvider.captureSnapshot(),
                    ),
                )
            }.getOrElse { error ->
                "capabilities(capture=FAILED, error=${error.message})"
            }
            val syncOrchestrator = MeasurementSequenceSyncOrchestrator(
                sequenceOrchestrator = sequenceOrchestrator,
                uploadTriggerUseCase = uploadTriggerUseCase,
            )
            val outcome = syncOrchestrator.run(request)
            val groupId = outcome.sequenceOutcome.group.id
            val persistedMeasurements = measurementRepo.getByGroupId(groupId)
            val persistenceSummary = CapabilityPersistenceSummaryFormatter.summarize(persistedMeasurements)
            val resultReadModel = MeasurementResultReadModelUseCase().present(
                MeasurementRunState(
                    progress = MeasurementRunProgress.END,
                    results = outcome.sequenceOutcome.group,
                    uploadTime = outcome.measurementCompleteUploadTime,
                ),
            )

            return IosPhase3SequenceSyncResult(
                groupId = groupId,
                throughputMachine = outcome.sequenceOutcome.throughputServerMachine,
                latencyMachine = outcome.sequenceOutcome.latencyServerMachine,
                submissionCreated = outcome.sequenceOutcome.group.submission != null,
                mapStartMeasurementsUploaded = outcome.mapStartReport.measurements.uploaded,
                mapStartSubmissionsUploaded = outcome.mapStartReport.submissions.uploaded,
                measurementCompleteUploadTimeSet = outcome.measurementCompleteUploadTime != null,
                measurementCompleteReportSummary = outcome.measurementCompleteReport.renderForStatus(),
                persistedMeasurements = persistedMeasurements.size,
                persistedSubmissions = if (submissionRepo.getById(groupId) != null) 1 else 0,
                capabilityPersistenceSummary = CapabilityPersistenceSummaryFormatter.format(persistenceSummary),
                capabilitySummary = capabilitySummary,
                latencySummary = resultReadModel.latencyText,
                downloadSummary = resultReadModel.downloadText,
                uploadSummary = resultReadModel.uploadText,
                completionSummary = resultReadModel.summaryText,
            )
        } finally {
            driver.close()
        }
    }
}

private class SequenceHarnessDeviceAuthStore(
    private val id: String = uuid4().toString(),
) : DeviceAuthStore {
    private var secret: String? = null

    override suspend fun getDeviceId(): String = id

    override suspend fun getDeviceSecret(): String? = secret

    override suspend fun saveDeviceSecret(secret: String) {
        this.secret = secret
    }
}
