package edu.gatech.cc.cellwatch.androidtestapp

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import edu.gatech.cc.cellwatch.androidtestapp.sync.AndroidTestSyncDriverFactory
import edu.gatech.cc.cellwatch.androidtestapp.sync.CellwatchPropertiesSupabaseEnvironmentProvider
import edu.gatech.cc.cellwatch.androidtestapp.sync.SupabaseTarget
import edu.gatech.cc.cellwatch.data.remote.DeviceAuthStore
import edu.gatech.cc.cellwatch.data.remote.SupabaseConnectionConfig
import edu.gatech.cc.cellwatch.data.remote.SupabaseMeasurementSyncRemoteDataSource
import edu.gatech.cc.cellwatch.data.repo.FccSubmissionRepositoryImpl
import edu.gatech.cc.cellwatch.data.repo.LatencyDataRepositoryImpl
import edu.gatech.cc.cellwatch.data.repo.MeasurementRepositoryImpl
import edu.gatech.cc.cellwatch.db.CellwatchDatabase
import edu.gatech.cc.cellwatch.domain.model.FccSubmission
import edu.gatech.cc.cellwatch.domain.model.LatencyData
import edu.gatech.cc.cellwatch.domain.model.Measurement
import edu.gatech.cc.cellwatch.domain.model.MeasurementGroup
import edu.gatech.cc.cellwatch.domain.model.NetworkConnectionType
import edu.gatech.cc.cellwatch.domain.model.TcpTuple
import edu.gatech.cc.cellwatch.domain.sync.SyncSmokeInvariantValidator
import edu.gatech.cc.cellwatch.domain.sync.TcpTupleProvider
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlin.coroutines.EmptyCoroutineContext

@RunWith(RobolectricTestRunner::class)
class LocalSupabaseSharedSyncSmokeTest {
    private val smokeValidator = SyncSmokeInvariantValidator()

    private lateinit var driver: AndroidSqliteDriver
    private lateinit var db: CellwatchDatabase

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        driver = AndroidSqliteDriver(
            schema = CellwatchDatabase.Schema,
            context = context,
            name = null,
        )
        db = CellwatchDatabase(driver)
    }

    @After
    fun tearDown() {
        if (this::driver.isInitialized) {
            driver.close()
        }
    }

    @Test
    fun syncAll_runsAgainstLocalSupabase_only() = runBlocking {
        val environmentProvider = CellwatchPropertiesSupabaseEnvironmentProvider()
        val localEnv = environmentProvider.resolve(SupabaseTarget.LOCAL)
        val localSupabaseReachable = isSupabaseReachable(localEnv.url)
        if (!localSupabaseReachable && System.getenv("CELLWATCH_ALLOW_LOCAL_SUPABASE_UNAVAILABLE_SKIP") == "1") {
            assumeTrue("Skipping local Supabase unavailable due to CELLWATCH_ALLOW_LOCAL_SUPABASE_UNAVAILABLE_SKIP=1", false)
        }
        assertTrue("Local Supabase unreachable at ${localEnv.url}", localSupabaseReachable)

        val authStore = InMemoryDeviceAuthStore()
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
            capabilityNotes = "location:permission denied in local smoke seed",
            connectionType = NetworkConnectionType.CELLULAR,
            cellularDataEnabled = true,
            latencyData = latency,
        )
        val submission = FccSubmission(
            id = groupId,
            deviceId = deviceId,
            provider = "android-test-app",
            submitted = false,
        )

        measurementRepo.upsert(measurement)
        latencyRepo.upsert(
            latency
        )
        submissionRepo.upsert(submission)

        val tcpProvider = object : TcpTupleProvider {
            override suspend fun getPublicTcpTuple(): TcpTuple = TcpTuple(
                remoteAddress = "203.0.113.20",
                remotePort = 443,
                timestamp = now.toEpochMilliseconds(),
            )
        }
        val syncDriver = AndroidTestSyncDriverFactory(
            database = db,
            deviceAuthStore = authStore,
            tcpTupleProvider = tcpProvider,
            environmentProvider = environmentProvider,
            io = EmptyCoroutineContext,
            clock = object : Clock {
                override fun now(): Instant = now
            },
        ).create(SupabaseTarget.LOCAL)
        val report = syncDriver.runMapStartSync()
        val nonNullReport = requireNotNull(report)
        assertNotNull(nonNullReport.measurements)
        assertNotNull(nonNullReport.submissions)
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
        assertEquals("AVAILABLE", syncedMeasurement?.telephonySupport)
        assertEquals("PARTIAL", syncedMeasurement?.networkSupport)
        assertNotNull(syncedMeasurement?.capabilityNotes)
        assertNotNull(submissionRepo.getById(groupId)?.uploadTime)

        val remoteVerifier = SupabaseMeasurementSyncRemoteDataSource(
            config = SupabaseConnectionConfig(
                url = localEnv.url.replace("10.0.2.2", "127.0.0.1"),
                apiKey = localEnv.apiKey,
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
    }

    private fun isSupabaseReachable(baseUrl: String): Boolean {
        return runCatching {
            val connection = (URL(baseUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 1500
                readTimeout = 1500
            }
            try {
                connection.responseCode in 200..499
            } finally {
                connection.disconnect()
            }
        }.getOrDefault(false)
    }
}

private class InMemoryDeviceAuthStore(
    private val deviceId: String = UUID.randomUUID().toString(),
) : DeviceAuthStore {
    private var secret: String? = null

    override suspend fun getDeviceId(): String = deviceId

    override suspend fun getDeviceSecret(): String? = secret

    override suspend fun saveDeviceSecret(secret: String) {
        this.secret = secret
    }
}
