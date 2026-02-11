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
        val measurementRepo = MeasurementRepositoryImpl(database.measurementQueries, io)
        val uploadRepo = UploadDownloadDataRepositoryImpl(database.uploadDownloadDataQueries, io)
        val latencyRepo = LatencyDataRepositoryImpl(database.latencyDataQueries, io)
        val locationRepo = LocationRepositoryImpl(database.locationQueries, io)
        val cellRepo = CellRepositoryImpl(database.cellQueries, io)
        val submissionRepo = FccSubmissionRepositoryImpl(database.fccSubmissionQueries, io)

        val localStore = RepositoryBackedMeasurementSyncLocalStore(
            measurementRepository = measurementRepo,
            uploadDownloadDataRepository = uploadRepo,
            latencyDataRepository = latencyRepo,
            locationRepository = locationRepo,
            cellRepository = cellRepo,
            fccSubmissionRepository = submissionRepo,
        )

        return create(
            localStore = localStore,
            remoteDataSource = remoteProvider.create(supabaseConfig),
            tcpTupleProvider = tcpTupleProvider,
            clock = clock,
        )
    }
}
