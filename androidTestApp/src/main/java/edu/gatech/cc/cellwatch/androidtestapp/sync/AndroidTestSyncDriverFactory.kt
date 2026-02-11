package edu.gatech.cc.cellwatch.androidtestapp.sync

import edu.gatech.cc.cellwatch.data.remote.DeviceAuthStore
import edu.gatech.cc.cellwatch.data.sync.DefaultSyncRemoteDataSourceFactory
import edu.gatech.cc.cellwatch.data.sync.MeasurementSyncServiceFactory
import edu.gatech.cc.cellwatch.data.sync.SyncRemoteProfile
import edu.gatech.cc.cellwatch.data.sync.SyncTransportTarget
import edu.gatech.cc.cellwatch.data.sync.SupabaseSyncRemoteDataSourceProvider
import edu.gatech.cc.cellwatch.db.CellwatchDatabase
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
        val remoteProvider = SupabaseSyncRemoteDataSourceProvider(deviceAuthStore)
        val uploadTriggerUseCase = MeasurementSyncServiceFactory.createUploadTriggerUseCase(
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
        return AndroidTestSyncDriver(uploadTriggerUseCase)
    }
}

private fun SupabaseTarget.toTransportTarget(): SyncTransportTarget {
    return when (this) {
        SupabaseTarget.LOCAL -> SyncTransportTarget.LOCAL
        SupabaseTarget.REMOTE -> SyncTransportTarget.REMOTE
    }
}
