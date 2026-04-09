package edu.gatech.cc.cellwatch.androidtestapp

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import edu.gatech.cc.cellwatch.androidtestapp.sync.AndroidTestSyncDriverFactory
import edu.gatech.cc.cellwatch.androidtestapp.sync.FixedSupabaseEnvironmentProvider
import edu.gatech.cc.cellwatch.androidtestapp.sync.SupabaseTarget
import edu.gatech.cc.cellwatch.androidtestapp.sync.resolveRuntimeProfileFromProperties
import edu.gatech.cc.cellwatch.data.remote.DeviceAuthStore
import edu.gatech.cc.cellwatch.data.remote.SupabaseConnectionConfig
import edu.gatech.cc.cellwatch.data.remote.SupabaseMeasurementSyncRemoteDataSource
import edu.gatech.cc.cellwatch.data.repo.FccSubmissionRepositoryImpl
import edu.gatech.cc.cellwatch.data.repo.LatencyDataRepositoryImpl
import edu.gatech.cc.cellwatch.data.repo.MeasurementRepositoryImpl
import edu.gatech.cc.cellwatch.data.sync.SyncTransportTarget
import edu.gatech.cc.cellwatch.db.CellwatchDatabase
import edu.gatech.cc.cellwatch.domain.fcc.MsakLocateEnvironment
import edu.gatech.cc.cellwatch.domain.model.FccSubmission
import edu.gatech.cc.cellwatch.domain.model.LatencyData
import edu.gatech.cc.cellwatch.domain.model.Measurement
import edu.gatech.cc.cellwatch.domain.model.MeasurementGroup
import edu.gatech.cc.cellwatch.domain.model.NetworkConnectionType
import edu.gatech.cc.cellwatch.domain.model.TcpTuple
import edu.gatech.cc.cellwatch.domain.runtime.RuntimeMsakMode
import edu.gatech.cc.cellwatch.domain.runtime.RuntimeSupabaseMode
import edu.gatech.cc.cellwatch.domain.sync.SyncSmokeInvariantValidator
import edu.gatech.cc.cellwatch.domain.sync.TcpTupleProvider
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.UUID
import kotlin.coroutines.EmptyCoroutineContext

@RunWith(RobolectricTestRunner::class)
class AndroidTestingSupabaseSmokeTest {
    private val smokeValidator = SyncSmokeInvariantValidator()

    @Test
    fun publicMsak_withTestingSupabaseProfile_runsHostedSync_whenEnabled() = runBlocking {
        assumeTrue(
            "Set CELLWATCH_RUN_ANDROID_TESTING_SUPABASE_SMOKE=1 to enable this Tier2 smoke test",
            System.getenv("CELLWATCH_RUN_ANDROID_TESTING_SUPABASE_SMOKE") == "1",
        )

        val expectedUrl = System.getenv("SUPABASE_TESTING_URL")?.trim('"')
        val expectedApiKey = System.getenv("SUPABASE_TESTING_API_KEY")?.trim('"')

        val profile = resolveRuntimeProfileFromProperties(
            msakMode = RuntimeMsakMode.PUBLIC,
            supabaseMode = RuntimeSupabaseMode.TESTING,
            allowRemoteSupabase = true,
        )
        val resolvedSupabase = profile.resolveSyncSupabaseConfig()
        val syncRemoteProfile = profile.toSyncRemoteProfile()

        assertEquals(MsakLocateEnvironment.PROD, profile.msakConfig.environment)
        assertEquals(RuntimeSupabaseMode.TESTING, profile.supabaseMode)
        assertEquals(SyncTransportTarget.REMOTE, syncRemoteProfile.target)
        assertTrue(
            "Expected hosted testing Supabase URL, got ${resolvedSupabase.url}",
            !resolvedSupabase.url.contains("127.0.0.1") && !resolvedSupabase.url.contains("localhost"),
        )
        if (!expectedUrl.isNullOrBlank()) {
            assertEquals(expectedUrl, resolvedSupabase.url)
        }
        if (!expectedApiKey.isNullOrBlank()) {
            assertEquals(expectedApiKey, resolvedSupabase.apiKey)
        }

        val context: Context = ApplicationProvider.getApplicationContext()
        val driver = AndroidSqliteDriver(
            schema = CellwatchDatabase.Schema,
            context = context,
            name = null,
        )
        try {
            val db = CellwatchDatabase(driver)
            val authStore = AndroidTestingInMemoryDeviceAuthStore()
            val deviceId = authStore.getDeviceId()
            val now = Clock.System.now()
            val groupId = UUID.randomUUID().toString()
            val measurementId = UUID.randomUUID().toString()

            val measurementRepo = MeasurementRepositoryImpl(db.measurementQueries, EmptyCoroutineContext)
            val latencyRepo = LatencyDataRepositoryImpl(db.latencyDataQueries, EmptyCoroutineContext)
            val submissionRepo = FccSubmissionRepositoryImpl(db.fccSubmissionQueries, EmptyCoroutineContext)

            val latency = LatencyData(
                id = UUID.randomUUID().toString(),
                measurementId = measurementId,
                rtt = 25,
                jitter = 2,
                sent = 10,
                received = 10,
            )
            val measurement = Measurement(
                id = measurementId,
                groupId = groupId,
                deviceId = deviceId,
                type = "latency",
                timestamp = now,
                telephonySupport = "AVAILABLE",
                networkSupport = "PARTIAL",
                locationSupport = "PERMISSION_DENIED",
                deviceSupport = "AVAILABLE",
                capabilityNotes = "location:permission denied in android testing smoke seed",
                connectionType = NetworkConnectionType.CELLULAR,
                cellularDataEnabled = true,
                latencyData = latency,
            )
            val submission = FccSubmission(
                id = groupId,
                deviceId = deviceId,
                provider = "android-test-app-testing-supabase",
                submitted = false,
            )
            measurementRepo.upsert(measurement)
            latencyRepo.upsert(latency)
            submissionRepo.upsert(submission)

            val syncDriver = AndroidTestSyncDriverFactory(
                database = db,
                deviceAuthStore = authStore,
                tcpTupleProvider = object : TcpTupleProvider {
                    override suspend fun getPublicTcpTuple(): TcpTuple = TcpTuple(
                        remoteAddress = "203.0.113.21",
                        remotePort = 443,
                        timestamp = now.toEpochMilliseconds(),
                    )
                },
                environmentProvider = FixedSupabaseEnvironmentProvider(profile.syncConfig),
                io = EmptyCoroutineContext,
                clock = object : Clock {
                    override fun now() = now
                },
            ).create(SupabaseTarget.REMOTE)

            val report = syncDriver.runMapStartSync()
            assertNotNull(report)
            val uploadTime = syncDriver.runMeasurementCompleteSync(
                MeasurementGroup(
                    latency = measurement,
                    download = null,
                    upload = null,
                    submission = submission,
                    id = groupId,
                )
            )
            assertNotNull(uploadTime)
            val syncedMeasurement = measurementRepo.getById(measurementId)
            assertNotNull(syncedMeasurement?.uploadTime)
            assertNotNull(submissionRepo.getById(groupId)?.uploadTime)

            val remoteVerifier = SupabaseMeasurementSyncRemoteDataSource(
                config = SupabaseConnectionConfig(
                    url = resolvedSupabase.url,
                    apiKey = resolvedSupabase.apiKey,
                ),
                deviceAuthStore = authStore,
            )
            val remoteMeasurement = remoteVerifier.getMeasurementById(measurementId)
            val invariantError = smokeValidator.validateSuccess(
                measurementUploadPersisted = syncedMeasurement?.uploadTime != null,
                submissionUploadPersisted = submissionRepo.getById(groupId)?.uploadTime != null,
                remoteMeasurementVerified = remoteMeasurement.id == measurementId,
                measurementCompleteUploadTimeSet = uploadTime != null,
            )
            assertNull(invariantError)
        } finally {
            driver.close()
        }
    }
}

private class AndroidTestingInMemoryDeviceAuthStore(
    private val id: String = UUID.randomUUID().toString(),
) : DeviceAuthStore {
    private var secret: String? = null

    override suspend fun getDeviceId(): String = id

    override suspend fun getDeviceSecret(): String? = secret

    override suspend fun saveDeviceSecret(secret: String) {
        this.secret = secret
    }
}
