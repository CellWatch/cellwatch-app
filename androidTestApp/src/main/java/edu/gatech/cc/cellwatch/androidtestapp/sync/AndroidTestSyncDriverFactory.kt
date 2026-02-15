package edu.gatech.cc.cellwatch.androidtestapp.sync

import edu.gatech.cc.cellwatch.data.remote.DeviceAuthStore
import edu.gatech.cc.cellwatch.data.sync.DefaultSyncRemoteDataSourceFactory
import edu.gatech.cc.cellwatch.data.sync.MeasurementSyncServiceFactory
import edu.gatech.cc.cellwatch.data.sync.SyncRemoteProfile
import edu.gatech.cc.cellwatch.data.sync.SyncTransportTarget
import edu.gatech.cc.cellwatch.data.sync.SupabaseSyncRemoteDataSourceProvider
import edu.gatech.cc.cellwatch.db.CellwatchDatabase
import edu.gatech.cc.cellwatch.domain.sync.UploadTriggerUseCase
import edu.gatech.cc.cellwatch.domain.sync.GetPendingSyncCountsUseCase
import edu.gatech.cc.cellwatch.domain.sync.RetryPendingSyncUseCase
import edu.gatech.cc.cellwatch.domain.sync.TcpTupleProvider
import kotlinx.datetime.Clock
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

class AndroidTestSyncDriverFactory(
    private val database: CellwatchDatabase,
    private val deviceAuthStore: DeviceAuthStore,
    private val tcpTupleProvider: TcpTupleProvider,
    private val environmentProvider: SupabaseEnvironmentProvider = CellwatchPropertiesSupabaseEnvironmentProvider(),
    private val io: CoroutineContext = EmptyCoroutineContext,
    private val clock: Clock = Clock.System,
) {
    fun create(target: SupabaseTarget = SupabaseTarget.LOCAL): AndroidTestSyncDriver {
        val uploadTriggerUseCase = createUploadTriggerUseCase(target)
        val pendingCountsUseCase = createPendingSyncCountsUseCase()
        val retryPendingSyncUseCase = createRetryPendingSyncUseCase(target)
        return AndroidTestSyncDriver(
            uploadTriggerUseCase = uploadTriggerUseCase,
            pendingCountsUseCase = pendingCountsUseCase,
            retryPendingSyncUseCase = retryPendingSyncUseCase,
        )
    }

    fun createUploadTriggerUseCase(target: SupabaseTarget = SupabaseTarget.LOCAL): UploadTriggerUseCase {
        val remoteProvider = SupabaseSyncRemoteDataSourceProvider(deviceAuthStore)
        return MeasurementSyncServiceFactory.createUploadTriggerUseCase(
            database = database,
            io = io,
            remoteProfile = SyncRemoteProfile.Supabase(
                configResolver = environmentProvider,
                target = target.toTransportTarget(),
            ),
            remoteFactory = DefaultSyncRemoteDataSourceFactory(remoteProvider),
            tcpTupleProvider = tcpTupleProvider,
            clock = clock,
        )
    }

    fun createPendingSyncCountsUseCase(): GetPendingSyncCountsUseCase {
        val repositories = MeasurementSyncServiceFactory.createRepositoriesForSyncUseCases(
            database = database,
            io = io,
        )
        return GetPendingSyncCountsUseCase(
            measurementRepository = repositories.measurementRepository,
            submissionRepository = repositories.submissionRepository,
        )
    }

    fun createRetryPendingSyncUseCase(target: SupabaseTarget = SupabaseTarget.LOCAL): RetryPendingSyncUseCase {
        val repositories = MeasurementSyncServiceFactory.createRepositoriesForSyncUseCases(
            database = database,
            io = io,
        )
        val syncService = MeasurementSyncServiceFactory.createSyncService(
            database = database,
            io = io,
            remoteProfile = SyncRemoteProfile.Supabase(
                configResolver = environmentProvider,
                target = target.toTransportTarget(),
            ),
            remoteFactory = DefaultSyncRemoteDataSourceFactory(
                SupabaseSyncRemoteDataSourceProvider(deviceAuthStore),
            ),
            tcpTupleProvider = tcpTupleProvider,
            clock = clock,
        )
        return RetryPendingSyncUseCase(
            syncService = syncService,
            pendingCountsUseCase = GetPendingSyncCountsUseCase(
                measurementRepository = repositories.measurementRepository,
                submissionRepository = repositories.submissionRepository,
            ),
        )
    }
}

private fun SupabaseTarget.toTransportTarget(): SyncTransportTarget {
    return when (this) {
        SupabaseTarget.LOCAL -> SyncTransportTarget.LOCAL
        SupabaseTarget.REMOTE -> SyncTransportTarget.REMOTE
    }
}
