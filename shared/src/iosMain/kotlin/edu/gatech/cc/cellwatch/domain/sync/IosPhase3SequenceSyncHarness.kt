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
import edu.gatech.cc.cellwatch.domain.capability.IosPlatformCapabilityProvider
import edu.gatech.cc.cellwatch.domain.fcc.DefaultMsakMeasurementSequenceOrchestratorFactory
import edu.gatech.cc.cellwatch.domain.fcc.MeasurementSequenceRequest
import edu.gatech.cc.cellwatch.domain.fcc.MsakLocateConfig
import edu.gatech.cc.cellwatch.domain.fcc.RepositoryBackedMeasurementResultStore
import edu.gatech.cc.cellwatch.domain.model.CollectionMode
import edu.gatech.cc.cellwatch.domain.model.TcpTuple
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.coroutines.EmptyCoroutineContext

data class IosPhase3SequenceSyncResult(
    val groupId: String,
    val throughputMachine: String,
    val latencyMachine: String,
    val submissionCreated: Boolean,
    val mapStartMeasurementsUploaded: Int,
    val mapStartSubmissionsUploaded: Int,
    val measurementCompleteUploadTimeSet: Boolean,
    val persistedMeasurements: Int,
    val persistedSubmissions: Int,
)

class IosPhase3SequenceSyncHarness {

    suspend fun run(
        msakConfig: MsakLocateConfig,
        supabaseUrl: String,
        supabaseApiKey: String,
    ): IosPhase3SequenceSyncResult {
        val now = Clock.System.now()
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
                capabilityProvider = IosPlatformCapabilityProvider(clock = object : Clock {
                    override fun now(): Instant = now
                }),
            )
            val syncOrchestrator = MeasurementSequenceSyncOrchestrator(
                sequenceOrchestrator = sequenceOrchestrator,
                uploadTriggerUseCase = uploadTriggerUseCase,
            )
            val request = MeasurementSequenceRequest(
                groupId = "phase3-${now.toEpochMilliseconds()}",
                inVehicle = false,
                mode = CollectionMode.FCC_CHALLENGE,
                measurementId = null,
            )
            val outcome = syncOrchestrator.run(request)
            val groupId = outcome.sequenceOutcome.group.id

            return IosPhase3SequenceSyncResult(
                groupId = groupId,
                throughputMachine = outcome.sequenceOutcome.throughputServerMachine,
                latencyMachine = outcome.sequenceOutcome.latencyServerMachine,
                submissionCreated = outcome.sequenceOutcome.group.submission != null,
                mapStartMeasurementsUploaded = outcome.mapStartReport.measurements.uploaded,
                mapStartSubmissionsUploaded = outcome.mapStartReport.submissions.uploaded,
                measurementCompleteUploadTimeSet = outcome.measurementCompleteUploadTime != null,
                persistedMeasurements = measurementRepo.getByGroupId(groupId).size,
                persistedSubmissions = if (submissionRepo.getById(groupId) != null) 1 else 0,
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
