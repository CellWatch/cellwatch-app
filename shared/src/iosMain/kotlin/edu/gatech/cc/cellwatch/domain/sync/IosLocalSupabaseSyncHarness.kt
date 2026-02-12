package edu.gatech.cc.cellwatch.domain.sync

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.benasher44.uuid.uuid4
import edu.gatech.cc.cellwatch.data.remote.DeviceAuthStore
import edu.gatech.cc.cellwatch.data.sync.MeasurementSyncServiceFactory
import edu.gatech.cc.cellwatch.data.sync.SupabaseSyncRemoteDataSourceProvider
import edu.gatech.cc.cellwatch.db.CellwatchDatabase
import edu.gatech.cc.cellwatch.domain.model.FccSubmission
import edu.gatech.cc.cellwatch.domain.model.LatencyData
import edu.gatech.cc.cellwatch.domain.model.Measurement
import edu.gatech.cc.cellwatch.domain.model.NetworkConnectionType
import edu.gatech.cc.cellwatch.domain.model.TcpTuple
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.coroutines.EmptyCoroutineContext

data class IosLocalSupabaseSyncResult(
    val measurementsAttempted: Int,
    val measurementsUploaded: Int,
    val submissionsAttempted: Int,
    val submissionsUploaded: Int,
    val measurementUploadPersisted: Boolean,
    val submissionUploadPersisted: Boolean,
)

class IosLocalSupabaseSyncHarness {

    suspend fun run(
        supabaseUrl: String,
        supabaseApiKey: String,
    ): IosLocalSupabaseSyncResult {
        val now = Clock.System.now()
        val deviceAuthStore = InMemoryDeviceAuthStore()
        val deviceId = deviceAuthStore.getDeviceId()
        val groupId = uuid4().toString()
        val measurementId = uuid4().toString()
        val latencyId = uuid4().toString()

        val driver = NativeSqliteDriver(
            schema = CellwatchDatabase.Schema,
            name = "ios-local-supabase-sync-${uuid4()}.db",
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
        val submissionRepo = edu.gatech.cc.cellwatch.data.repo.FccSubmissionRepositoryImpl(
            queries = db.fccSubmissionQueries,
            io = EmptyCoroutineContext,
        )

        try {
            measurementRepo.upsert(
                Measurement(
                    id = measurementId,
                    groupId = groupId,
                    deviceId = deviceId,
                    type = "latency",
                    timestamp = now,
                    connectionType = NetworkConnectionType.CELLULAR,
                    cellularDataEnabled = true,
                ),
            )
            latencyRepo.upsert(
                LatencyData(
                    id = latencyId,
                    measurementId = measurementId,
                    rtt = 25,
                    jitter = 2,
                    sent = 10,
                    received = 10,
                ),
            )
            submissionRepo.upsert(
                FccSubmission(
                    id = groupId,
                    deviceId = deviceId,
                    provider = "ios-hosted-local",
                    submitted = false,
                ),
            )

            val syncService = MeasurementSyncServiceFactory.createSupabaseBacked(
                database = db,
                io = EmptyCoroutineContext,
                supabaseConfig = MeasurementSyncServiceFactory.resolveSupabaseConfigForRuntime(
                    allowRemote = false,
                    localUrl = supabaseUrl,
                    localApiKey = supabaseApiKey,
                    remoteUrl = null,
                    remoteApiKey = null,
                    useRemote = false,
                ),
                remoteProvider = SupabaseSyncRemoteDataSourceProvider(deviceAuthStore = deviceAuthStore),
                tcpTupleProvider = object : TcpTupleProvider {
                    override suspend fun getPublicTcpTuple(): TcpTuple {
                        return TcpTuple(
                            remoteAddress = "203.0.113.10",
                            remotePort = 4242,
                            timestamp = now.toEpochMilliseconds(),
                        )
                    }
                },
                clock = object : Clock {
                    override fun now(): Instant = now
                },
            )

            val measurementReport = syncService.syncMeasurements()
            val submissionReport = syncService.syncFccSubmissions()

            val syncedMeasurement = measurementRepo.getById(measurementId)
            val syncedSubmission = submissionRepo.getById(groupId)

            return IosLocalSupabaseSyncResult(
                measurementsAttempted = measurementReport.attempted,
                measurementsUploaded = measurementReport.uploaded,
                submissionsAttempted = submissionReport.attempted,
                submissionsUploaded = submissionReport.uploaded,
                measurementUploadPersisted = syncedMeasurement?.uploadTime != null,
                submissionUploadPersisted = syncedSubmission?.uploadTime != null,
            )
        } finally {
            driver.close()
        }
    }
}

private class InMemoryDeviceAuthStore(
    private val id: String = uuid4().toString(),
) : DeviceAuthStore {
    private var secret: String? = null

    override suspend fun getDeviceId(): String = id

    override suspend fun getDeviceSecret(): String? = secret

    override suspend fun saveDeviceSecret(secret: String) {
        this.secret = secret
    }
}
