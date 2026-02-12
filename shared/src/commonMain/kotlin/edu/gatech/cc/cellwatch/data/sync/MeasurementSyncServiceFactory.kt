package edu.gatech.cc.cellwatch.data.sync

import edu.gatech.cc.cellwatch.data.remote.DeviceAuthStore
import edu.gatech.cc.cellwatch.data.remote.SupabaseConnectionConfig
import edu.gatech.cc.cellwatch.data.remote.SupabaseMeasurementSyncRemoteDataSource
import edu.gatech.cc.cellwatch.data.repo.CellRepositoryImpl
import edu.gatech.cc.cellwatch.data.repo.FccSubmissionRepositoryImpl
import edu.gatech.cc.cellwatch.data.repo.LatencyDataRepositoryImpl
import edu.gatech.cc.cellwatch.data.repo.LocationRepositoryImpl
import edu.gatech.cc.cellwatch.data.repo.MeasurementRepositoryImpl
import edu.gatech.cc.cellwatch.data.repo.UploadDownloadDataRepositoryImpl
import edu.gatech.cc.cellwatch.db.CellwatchDatabase
import edu.gatech.cc.cellwatch.domain.sync.DefaultMeasurementSyncService
import edu.gatech.cc.cellwatch.domain.sync.MeasurementSyncRemoteDataSource
import edu.gatech.cc.cellwatch.domain.sync.MeasurementSyncService
import edu.gatech.cc.cellwatch.domain.sync.MeasurementSyncUseCase
import edu.gatech.cc.cellwatch.domain.sync.TcpTupleProvider
import edu.gatech.cc.cellwatch.domain.sync.UploadTriggerUseCase
import kotlinx.datetime.Clock
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

data class SyncSupabaseConfig(
    val url: String,
    val apiKey: String,
)

interface SyncRemoteDataSourceProvider {
    fun create(config: SyncSupabaseConfig): MeasurementSyncRemoteDataSource
}

class SupabaseSyncRemoteDataSourceProvider(
    private val deviceAuthStore: DeviceAuthStore,
) : SyncRemoteDataSourceProvider {
    override fun create(config: SyncSupabaseConfig): MeasurementSyncRemoteDataSource =
        SupabaseMeasurementSyncRemoteDataSource(
            config = SupabaseConnectionConfig(
                url = config.url,
                apiKey = config.apiKey,
            ),
            deviceAuthStore = deviceAuthStore,
        )
}

object MeasurementSyncServiceFactory {

    private data class SyncRepositories(
        val measurementRepo: MeasurementRepositoryImpl,
        val uploadRepo: UploadDownloadDataRepositoryImpl,
        val latencyRepo: LatencyDataRepositoryImpl,
        val locationRepo: LocationRepositoryImpl,
        val cellRepo: CellRepositoryImpl,
        val submissionRepo: FccSubmissionRepositoryImpl,
    )

    fun create(
        localStore: edu.gatech.cc.cellwatch.domain.sync.MeasurementSyncLocalStore,
        remoteDataSource: MeasurementSyncRemoteDataSource,
        tcpTupleProvider: TcpTupleProvider,
        clock: Clock = Clock.System,
    ): MeasurementSyncService {
        val useCase = MeasurementSyncUseCase(
            localStore = localStore,
            remoteDataSource = remoteDataSource,
            tcpTupleProvider = tcpTupleProvider,
            clock = clock,
        )
        return DefaultMeasurementSyncService(useCase)
    }

    fun createSupabaseBacked(
        database: CellwatchDatabase,
        io: CoroutineContext = EmptyCoroutineContext,
        supabaseConfig: SyncSupabaseConfig,
        remoteProvider: SyncRemoteDataSourceProvider,
        tcpTupleProvider: TcpTupleProvider,
        clock: Clock = Clock.System,
    ): MeasurementSyncService {
        val repos = createRepositories(database, io)

        val localStore = RepositoryBackedMeasurementSyncLocalStore(
            measurementRepository = repos.measurementRepo,
            uploadDownloadDataRepository = repos.uploadRepo,
            latencyDataRepository = repos.latencyRepo,
            locationRepository = repos.locationRepo,
            cellRepository = repos.cellRepo,
            fccSubmissionRepository = repos.submissionRepo,
        )

        return create(
            localStore = localStore,
            remoteDataSource = remoteProvider.create(supabaseConfig),
            tcpTupleProvider = tcpTupleProvider,
            clock = clock,
        )
    }

    fun createSupabaseUploadTriggerUseCase(
        database: CellwatchDatabase,
        io: CoroutineContext = EmptyCoroutineContext,
        supabaseConfig: SyncSupabaseConfig,
        remoteProvider: SyncRemoteDataSourceProvider,
        tcpTupleProvider: TcpTupleProvider,
        clock: Clock = Clock.System,
    ): UploadTriggerUseCase {
        val repos = createRepositories(database, io)
        val syncService = createSupabaseBacked(
            database = database,
            io = io,
            supabaseConfig = supabaseConfig,
            remoteProvider = remoteProvider,
            tcpTupleProvider = tcpTupleProvider,
            clock = clock,
        )
        return UploadTriggerUseCase(
            syncService = syncService,
            measurementRepository = repos.measurementRepo,
            submissionRepository = repos.submissionRepo,
        )
    }

    fun createSupabaseUploadTriggerUseCase(
        database: CellwatchDatabase,
        io: CoroutineContext = EmptyCoroutineContext,
        supabaseConfigResolver: SyncSupabaseConfigResolver,
        transportTarget: SyncTransportTarget = SyncTransportTarget.LOCAL,
        remoteProvider: SyncRemoteDataSourceProvider,
        tcpTupleProvider: TcpTupleProvider,
        clock: Clock = Clock.System,
    ): UploadTriggerUseCase {
        return createSupabaseUploadTriggerUseCase(
            database = database,
            io = io,
            supabaseConfig = supabaseConfigResolver.resolve(transportTarget),
            remoteProvider = remoteProvider,
            tcpTupleProvider = tcpTupleProvider,
            clock = clock,
        )
    }

    fun createUploadTriggerUseCase(
        database: CellwatchDatabase,
        io: CoroutineContext = EmptyCoroutineContext,
        remoteProfile: SyncRemoteProfile,
        remoteFactory: SyncRemoteDataSourceFactory,
        tcpTupleProvider: TcpTupleProvider,
        clock: Clock = Clock.System,
    ): UploadTriggerUseCase {
        val repos = createRepositories(database, io)
        val localStore = RepositoryBackedMeasurementSyncLocalStore(
            measurementRepository = repos.measurementRepo,
            uploadDownloadDataRepository = repos.uploadRepo,
            latencyDataRepository = repos.latencyRepo,
            locationRepository = repos.locationRepo,
            cellRepository = repos.cellRepo,
            fccSubmissionRepository = repos.submissionRepo,
        )
        val syncService = create(
            localStore = localStore,
            remoteDataSource = remoteFactory.create(remoteProfile),
            tcpTupleProvider = tcpTupleProvider,
            clock = clock,
        )
        return UploadTriggerUseCase(
            syncService = syncService,
            measurementRepository = repos.measurementRepo,
            submissionRepository = repos.submissionRepo,
        )
    }

    @Throws(IllegalStateException::class)
    fun resolveSupabaseConfigForRuntime(
        allowRemote: Boolean = false,
        localUrl: String? = null,
        localApiKey: String? = null,
        remoteUrl: String? = null,
        remoteApiKey: String? = null,
        useRemote: Boolean = false,
    ): SyncSupabaseConfig {
        return SyncRuntimeProfileBridge.resolveSupabaseConfig(
            allowRemote = allowRemote,
            localUrl = localUrl,
            localApiKey = localApiKey,
            remoteUrl = remoteUrl,
            remoteApiKey = remoteApiKey,
            useRemote = useRemote,
        )
    }

    private fun createRepositories(
        database: CellwatchDatabase,
        io: CoroutineContext,
    ): SyncRepositories {
        val measurementRepo = MeasurementRepositoryImpl(database.measurementQueries, io)
        val uploadRepo = UploadDownloadDataRepositoryImpl(database.uploadDownloadDataQueries, io)
        val latencyRepo = LatencyDataRepositoryImpl(database.latencyDataQueries, io)
        val locationRepo = LocationRepositoryImpl(database.locationQueries, io)
        val cellRepo = CellRepositoryImpl(database.cellQueries, io)
        val submissionRepo = FccSubmissionRepositoryImpl(database.fccSubmissionQueries, io)
        return SyncRepositories(
            measurementRepo = measurementRepo,
            uploadRepo = uploadRepo,
            latencyRepo = latencyRepo,
            locationRepo = locationRepo,
            cellRepo = cellRepo,
            submissionRepo = submissionRepo,
        )
    }
}
