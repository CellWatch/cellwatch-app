package edu.gatech.cc.cellwatch.androidtestapp

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import edu.gatech.cc.cellwatch.androidtestapp.sync.AndroidTestSyncDriverFactory
import edu.gatech.cc.cellwatch.androidtestapp.sync.FixedSupabaseEnvironmentProvider
import edu.gatech.cc.cellwatch.androidtestapp.sync.SupabaseTarget
import edu.gatech.cc.cellwatch.data.remote.DeviceAuthStore
import edu.gatech.cc.cellwatch.data.remote.SupabaseConnectionConfig
import edu.gatech.cc.cellwatch.data.remote.SupabaseMeasurementSyncRemoteDataSource
import edu.gatech.cc.cellwatch.data.repo.FccSubmissionRepositoryImpl
import edu.gatech.cc.cellwatch.data.repo.LatencyDataRepositoryImpl
import edu.gatech.cc.cellwatch.data.repo.MeasurementRepositoryImpl
import edu.gatech.cc.cellwatch.data.sync.SyncTransportTarget
import edu.gatech.cc.cellwatch.db.CellwatchDatabase
import edu.gatech.cc.cellwatch.domain.capability.AndroidPlatformCapabilityProvider
import edu.gatech.cc.cellwatch.domain.fcc.MeasurementSequenceHarness
import edu.gatech.cc.cellwatch.domain.fcc.MeasurementSequenceHarnessResult
import edu.gatech.cc.cellwatch.domain.fcc.MsakLocateEnvironment
import edu.gatech.cc.cellwatch.domain.model.FccSubmission
import edu.gatech.cc.cellwatch.domain.model.LatencyData
import edu.gatech.cc.cellwatch.domain.model.Measurement
import edu.gatech.cc.cellwatch.domain.model.MeasurementGroup
import edu.gatech.cc.cellwatch.domain.model.NetworkConnectionType
import edu.gatech.cc.cellwatch.domain.model.TcpTuple
import edu.gatech.cc.cellwatch.domain.runtime.RuntimeSyncMsakProfiles
import edu.gatech.cc.cellwatch.domain.sync.SyncSmokeInvariantValidator
import edu.gatech.cc.cellwatch.domain.sync.TcpTupleProvider
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.Properties
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.coroutines.EmptyCoroutineContext

@RunWith(RobolectricTestRunner::class)
class PublicMsakLocalSupabaseSmokeTest {
    private val smokeValidator = SyncSmokeInvariantValidator()

    @Test
    fun publicMsak_withLocalSupabaseProfile_runsPhase3AndStoreForward_whenEnabled() = runBlocking {
        assumeTrue(
            "Set CELLWATCH_RUN_PUBLIC_MSAK_LOCAL_SUPABASE_SMOKE=1 to enable this Tier2 smoke test",
            System.getenv("CELLWATCH_RUN_PUBLIC_MSAK_LOCAL_SUPABASE_SMOKE") == "1",
        )

        val props = loadCellwatchProperties()
        val profile = RuntimeSyncMsakProfiles.publicMsakLocalSupabase(
            localSupabaseUrl = props.getProperty("SUPABASE_LOCAL_URL"),
            localSupabaseApiKey = props.getProperty("SUPABASE_LOCAL_API_KEY"),
            userAgent = "android-test-app-phase3-public-msak-local-supabase",
        )
        val resolvedSupabase = profile.resolveSyncSupabaseConfig()
        val syncRemoteProfile = profile.toSyncRemoteProfile()

        assertEquals(MsakLocateEnvironment.PROD, profile.msakConfig.environment)
        assertEquals(SyncTransportTarget.LOCAL, syncRemoteProfile.target)
        assertTrue(
            "Expected local-only Supabase URL, got ${resolvedSupabase.url}",
            resolvedSupabase.url.contains("127.0.0.1") || resolvedSupabase.url.contains("localhost"),
        )

        val latch = CountDownLatch(1)
        var result: MeasurementSequenceHarnessResult? = null
        var error: Throwable? = null

        val harness = MeasurementSequenceHarness(
            config = profile.msakConfig,
            capabilityProvider = AndroidPlatformCapabilityProvider(
                ApplicationProvider.getApplicationContext(),
            ),
        )
        harness.runDefaultScenario { value, throwable ->
            result = value
            error = throwable
            latch.countDown()
        }

        val completed = latch.await(90, TimeUnit.SECONDS)
        harness.close()
        assertTrue("Phase3 public MSAK smoke test timed out", completed)
        if (error != null) throw AssertionError("Phase3 public MSAK smoke failed", error)

        val nonNull = requireNotNull(result)
        assertNotNull(nonNull.groupId)
        assertTrue(nonNull.throughputMachine.isNotBlank())
        assertTrue(nonNull.latencyMachine.isNotBlank())
        assertEquals(3, nonNull.persistedMeasurements)
        assertEquals(nonNull.persistedMeasurements, nonNull.persistedMeasurementsWithCapabilitySupport)
        assertTrue(nonNull.persistedMeasurementsWithCapabilityNotes >= 0)
        assertTrue(nonNull.capabilityPersistenceSummary.startsWith("capabilityPersistence("))
        assertTrue(nonNull.capabilitySummary.startsWith("capabilities("))

        assumeTrue("local supabase unavailable", isSupabaseReachable(resolvedSupabase.url))

        val context: Context = ApplicationProvider.getApplicationContext()
        val driver = AndroidSqliteDriver(
            schema = CellwatchDatabase.Schema,
            context = context,
            name = null,
        )
        try {
            val db = CellwatchDatabase(driver)
            val authStore = PublicMsakInMemoryDeviceAuthStore()
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
                capabilityNotes = "location:permission denied in public/local smoke seed",
                connectionType = NetworkConnectionType.CELLULAR,
                cellularDataEnabled = true,
                latencyData = latency,
            )
            val submission = FccSubmission(
                id = groupId,
                deviceId = deviceId,
                provider = "android-test-app-public-msak-local-supabase",
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
                        remoteAddress = "203.0.113.20",
                        remotePort = 443,
                        timestamp = now.toEpochMilliseconds(),
                    )
                },
                environmentProvider = FixedSupabaseEnvironmentProvider(profile.syncConfig),
                io = EmptyCoroutineContext,
                clock = object : Clock {
                    override fun now() = now
                },
            ).create(SupabaseTarget.LOCAL)

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
            assertEquals("AVAILABLE", syncedMeasurement?.telephonySupport)
            assertEquals("PARTIAL", syncedMeasurement?.networkSupport)
            assertNotNull(syncedMeasurement?.capabilityNotes)
            assertNotNull(submissionRepo.getById(groupId)?.uploadTime)

            val remoteVerifier = SupabaseMeasurementSyncRemoteDataSource(
                config = SupabaseConnectionConfig(
                    url = resolvedSupabase.url.replace("10.0.2.2", "127.0.0.1"),
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

private fun loadCellwatchProperties(): Properties {
    val propsFile = listOf(
        File("cellwatch.properties"),
        File("../cellwatch.properties"),
    ).firstOrNull { it.exists() }
        ?: throw AssertionError("Missing cellwatch.properties in test working directory")
    return Properties().apply {
        FileInputStream(propsFile).use(::load)
    }
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

private class PublicMsakInMemoryDeviceAuthStore(
    private val deviceId: String = UUID.randomUUID().toString(),
) : DeviceAuthStore {
    private var secret: String? = null

    override suspend fun getDeviceId(): String = deviceId

    override suspend fun getDeviceSecret(): String? = secret

    override suspend fun saveDeviceSecret(secret: String) {
        this.secret = secret
    }
}
