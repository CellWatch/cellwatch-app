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
import edu.gatech.cc.cellwatch.domain.model.FccSubmission
import edu.gatech.cc.cellwatch.domain.model.LatencyData
import edu.gatech.cc.cellwatch.domain.model.Measurement
import edu.gatech.cc.cellwatch.domain.model.NetworkConnectionType
import edu.gatech.cc.cellwatch.domain.model.TcpTuple
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.coroutines.EmptyCoroutineContext

class IosPendingSyncHarness {
    fun runPendingCountsAsync(
        onComplete: (String?, Throwable?) -> Unit,
    ) {
        val handler = CoroutineExceptionHandler { _, throwable ->
            onComplete(null, throwable)
        }
        CoroutineScope(SupervisorJob() + Dispatchers.Default + handler).launch {
            runCatching { runPendingCounts() }
                .onSuccess { onComplete(it, null) }
                .onFailure { onComplete(null, it) }
        }
    }

    fun runRetryPendingSyncAsync(
        supabaseUrl: String,
        supabaseApiKey: String,
        syncEnabled: Boolean = true,
        onComplete: (String?, Throwable?) -> Unit,
    ) {
        val handler = CoroutineExceptionHandler { _, throwable ->
            onComplete(null, throwable)
        }
        CoroutineScope(SupervisorJob() + Dispatchers.Default + handler).launch {
            runCatching {
                runRetryPendingSync(
                    supabaseUrl = supabaseUrl,
                    supabaseApiKey = supabaseApiKey,
                    syncEnabled = syncEnabled,
                )
            }
                .onSuccess { onComplete(it, null) }
                .onFailure { onComplete(null, it) }
        }
    }

    private suspend fun runPendingCounts(): String {
        val driver = NativeSqliteDriver(
            schema = CellwatchDatabase.Schema,
            name = "ios-pending-sync-counts-${uuid4()}.db",
        )
        val db = CellwatchDatabase(driver)
        try {
            seedPendingRecords(db = db, now = Clock.System.now())
            val repositories = MeasurementSyncServiceFactory.createRepositoriesForSyncUseCases(
                database = db,
                io = EmptyCoroutineContext,
            )
            val counts = GetPendingSyncCountsUseCase(
                measurementRepository = repositories.measurementRepository,
                submissionRepository = repositories.submissionRepository,
            ).execute()
            return "Pending sync counts:\nmeasurements=${counts.measurements}\nsubmissions=${counts.submissions}\ntotal=${counts.total}"
        } finally {
            driver.close()
        }
    }

    private suspend fun runRetryPendingSync(
        supabaseUrl: String,
        supabaseApiKey: String,
        syncEnabled: Boolean,
    ): String {
        val now = Clock.System.now()
        val driver = NativeSqliteDriver(
            schema = CellwatchDatabase.Schema,
            name = "ios-pending-sync-retry-${uuid4()}.db",
        )
        val db = CellwatchDatabase(driver)
        val deviceAuthStore = PendingSyncDeviceAuthStore()
        try {
            seedPendingRecords(db = db, now = now)
            val useCase = if (syncEnabled) {
                MeasurementSyncServiceFactory.createRetryPendingSyncUseCase(
                    database = db,
                    io = EmptyCoroutineContext,
                    remoteProfile = SyncRemoteProfile.Supabase(
                        configResolver = SyncRuntimeConfigFactory.fromRaw(
                            allowRemote = false,
                            localUrl = supabaseUrl,
                            localApiKey = supabaseApiKey,
                        ),
                        target = SyncTransportTarget.LOCAL,
                    ),
                    remoteFactory = DefaultSyncRemoteDataSourceFactory(
                        SupabaseSyncRemoteDataSourceProvider(deviceAuthStore = deviceAuthStore),
                    ),
                    tcpTupleProvider = object : TcpTupleProvider {
                        override suspend fun getPublicTcpTuple(): TcpTuple {
                            return TcpTuple(
                                remoteAddress = "203.0.113.12",
                                remotePort = 4242,
                                timestamp = now.toEpochMilliseconds(),
                            )
                        }
                    },
                    clock = object : Clock {
                        override fun now(): Instant = now
                    },
                )
            } else {
                MeasurementSyncServiceFactory.createLocalOnlyRetryPendingSyncUseCase(
                    database = db,
                    io = EmptyCoroutineContext,
                )
            }
            val summary = useCase.execute()
            return buildString {
                appendLine("Retry pending sync:")
                appendLine("status=${summary.status}")
                appendLine("before(total=${summary.before.total}, measurements=${summary.before.measurements}, submissions=${summary.before.submissions})")
                appendLine("after(total=${summary.after.total}, measurements=${summary.after.measurements}, submissions=${summary.after.submissions})")
                append(summary.userMessage)
            }
        } finally {
            driver.close()
        }
    }

    private suspend fun seedPendingRecords(
        db: CellwatchDatabase,
        now: Instant,
    ) {
        val deviceId = "ios-pending-sync-device"
        val groupId = uuid4().toString()
        val measurementId = uuid4().toString()
        val latencyId = uuid4().toString()

        val measurementRepo = edu.gatech.cc.cellwatch.data.repo.MeasurementRepositoryImpl(
            queries = db.measurementQueries,
            io = EmptyCoroutineContext,
        )
        val latencyRepo = edu.gatech.cc.cellwatch.data.repo.LatencyDataRepositoryImpl(
            queries = db.latencyDataQueries,
            io = EmptyCoroutineContext,
        )
        val submissionRepo = edu.gatech.cc.cellwatch.data.repo.FccSubmissionRepositoryImpl(
            queries = db.fccSubmissionQueries,
            io = EmptyCoroutineContext,
        )

        val measurement = Measurement(
            id = measurementId,
            groupId = groupId,
            deviceId = deviceId,
            type = "latency",
            timestamp = now,
            connectionType = NetworkConnectionType.CELLULAR,
            cellularDataEnabled = true,
            latencyData = LatencyData(
                id = latencyId,
                measurementId = measurementId,
                rtt = 19,
                jitter = 2,
                sent = 8,
                received = 8,
            ),
        )
        measurementRepo.upsert(measurement)
        latencyRepo.upsert(
            LatencyData(
                id = latencyId,
                measurementId = measurementId,
                rtt = 19,
                jitter = 2,
                sent = 8,
                received = 8,
            ),
        )
        submissionRepo.upsert(
            FccSubmission(
                id = groupId,
                deviceId = deviceId,
                provider = "ios-pending-sync-harness",
                submitted = false,
            ),
        )
    }
}

private class PendingSyncDeviceAuthStore(
    private val id: String = uuid4().toString(),
) : DeviceAuthStore {
    private var secret: String? = null

    override suspend fun getDeviceId(): String = id

    override suspend fun getDeviceSecret(): String? = secret

    override suspend fun saveDeviceSecret(secret: String) {
        this.secret = secret
    }
}
