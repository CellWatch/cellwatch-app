package edu.gatech.cc.cellwatch.domain.sync

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.benasher44.uuid.uuid4
import edu.gatech.cc.cellwatch.data.remote.DeviceAuthStore
import edu.gatech.cc.cellwatch.data.remote.SupabaseConnectionConfig
import edu.gatech.cc.cellwatch.data.remote.SupabaseMeasurementSyncRemoteDataSource
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
import edu.gatech.cc.cellwatch.domain.model.MeasurementGroup
import edu.gatech.cc.cellwatch.domain.model.NetworkConnectionType
import edu.gatech.cc.cellwatch.domain.model.TcpTuple
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.coroutines.EmptyCoroutineContext

data class IosLocalSupabaseSyncResult(
    val mapStartMeasurementsAttempted: Int,
    val mapStartMeasurementsUploaded: Int,
    val mapStartSubmissionsAttempted: Int,
    val mapStartSubmissionsUploaded: Int,
    val measurementCompleteUploadTimeSet: Boolean,
    val measurementUploadPersisted: Boolean,
    val submissionUploadPersisted: Boolean,
    val remoteMeasurementVerified: Boolean,
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
                    rtt = 25,
                    jitter = 2,
                    sent = 10,
                    received = 10,
                ),
            )
            measurementRepo.upsert(measurement)
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
            val submission = FccSubmission(
                id = groupId,
                deviceId = deviceId,
                provider = "ios-hosted-local",
                submitted = false,
            )
            submissionRepo.upsert(submission)

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

            val group = MeasurementGroup(
                latency = measurement,
                download = null,
                upload = null,
                submission = submission,
                id = groupId,
            )
            val mapStartReport = uploadTriggerUseCase.onMapStart()
            val measurementCompleteUploadTime = uploadTriggerUseCase.onMeasurementComplete(group)

            val syncedMeasurement = measurementRepo.getById(measurementId)
            val syncedSubmission = submissionRepo.getById(groupId)
            val remoteVerifier = SupabaseMeasurementSyncRemoteDataSource(
                config = SupabaseConnectionConfig(
                    url = supabaseUrl.replace("10.0.2.2", "127.0.0.1"),
                    apiKey = supabaseApiKey,
                ),
                deviceAuthStore = deviceAuthStore,
            )
            val remoteMeasurement = remoteVerifier.getMeasurementById(measurementId)

            return IosLocalSupabaseSyncResult(
                mapStartMeasurementsAttempted = mapStartReport.measurements.attempted,
                mapStartMeasurementsUploaded = mapStartReport.measurements.uploaded,
                mapStartSubmissionsAttempted = mapStartReport.submissions.attempted,
                mapStartSubmissionsUploaded = mapStartReport.submissions.uploaded,
                measurementCompleteUploadTimeSet = measurementCompleteUploadTime != null,
                measurementUploadPersisted = syncedMeasurement?.uploadTime != null,
                submissionUploadPersisted = syncedSubmission?.uploadTime != null,
                remoteMeasurementVerified = remoteMeasurement.id == measurementId,
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
