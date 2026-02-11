package edu.gatech.cc.cellwatch.androidtestapp.sync

import edu.gatech.cc.cellwatch.data.remote.DeviceAuthStore
import edu.gatech.cc.cellwatch.data.repo.FccSubmissionRepositoryImpl
import edu.gatech.cc.cellwatch.data.repo.MeasurementRepositoryImpl
import edu.gatech.cc.cellwatch.data.sync.MeasurementSyncServiceFactory
import edu.gatech.cc.cellwatch.data.sync.SupabaseSyncRemoteDataSourceProvider
import edu.gatech.cc.cellwatch.data.sync.SyncSupabaseConfig
import edu.gatech.cc.cellwatch.db.CellwatchDatabase
import edu.gatech.cc.cellwatch.domain.sync.TcpTupleProvider
import edu.gatech.cc.cellwatch.domain.sync.UploadTriggerUseCase
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
        val env = environmentProvider.resolve(target)
        val syncService = MeasurementSyncServiceFactory.createSupabaseBacked(
            database = database,
            io = io,
            supabaseConfig = SyncSupabaseConfig(
                url = env.url,
                apiKey = env.apiKey,
            ),
            remoteProvider = SupabaseSyncRemoteDataSourceProvider(deviceAuthStore),
            tcpTupleProvider = tcpTupleProvider,
            clock = clock,
        )
        val measurementRepo = MeasurementRepositoryImpl(database.measurementQueries, io)
        val submissionRepo = FccSubmissionRepositoryImpl(database.fccSubmissionQueries, io)
        val uploadTriggerUseCase = UploadTriggerUseCase(
            syncService = syncService,
            measurementRepository = measurementRepo,
            submissionRepository = submissionRepo,
        )
        return AndroidTestSyncDriver(uploadTriggerUseCase)
    }
}
