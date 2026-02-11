package edu.gatech.cc.cellwatch.domain.sync

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import edu.gatech.cc.cellwatch.data.remote.InMemoryDeviceAuthStore
import edu.gatech.cc.cellwatch.data.remote.SupabaseMeasurementSyncRemoteDataSource
import edu.gatech.cc.cellwatch.data.remote.loadLocalSupabaseConfig
import edu.gatech.cc.cellwatch.data.repo.CellRepositoryImpl
import edu.gatech.cc.cellwatch.data.repo.FccSubmissionRepositoryImpl
import edu.gatech.cc.cellwatch.data.repo.LatencyDataRepositoryImpl
import edu.gatech.cc.cellwatch.data.repo.LocationRepositoryImpl
import edu.gatech.cc.cellwatch.data.repo.MeasurementRepositoryImpl
import edu.gatech.cc.cellwatch.data.repo.UploadDownloadDataRepositoryImpl
import edu.gatech.cc.cellwatch.data.sync.RepositoryBackedMeasurementSyncLocalStore
import edu.gatech.cc.cellwatch.db.CellwatchDatabase
import edu.gatech.cc.cellwatch.domain.model.FccSubmission
import edu.gatech.cc.cellwatch.domain.model.LatencyData
import edu.gatech.cc.cellwatch.domain.model.Measurement
import edu.gatech.cc.cellwatch.domain.model.NetworkConnectionType
import edu.gatech.cc.cellwatch.domain.model.TcpTuple
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.util.UUID
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class MeasurementSyncUseCaseLocalSupabaseIntegrationTest {

    private lateinit var driver: JdbcSqliteDriver

    @AfterTest
    fun tearDown() {
        if (this::driver.isInitialized) {
            driver.close()
        }
    }

    @Test
    fun syncMeasurements_andSubmissions_marksLocalUploadTime() = runBlocking {
        val deviceAuth = InMemoryDeviceAuthStore()
        val deviceId = deviceAuth.getDeviceId()
        val remote = SupabaseMeasurementSyncRemoteDataSource(
            config = loadLocalSupabaseConfig(),
            deviceAuthStore = deviceAuth,
        )

        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        CellwatchDatabase.Schema.create(driver)
        val db = CellwatchDatabase(driver)

        val measurementRepo = MeasurementRepositoryImpl(db.measurementQueries, EmptyCoroutineContext)
        val uploadRepo = UploadDownloadDataRepositoryImpl(db.uploadDownloadDataQueries, EmptyCoroutineContext)
        val latencyRepo = LatencyDataRepositoryImpl(db.latencyDataQueries, EmptyCoroutineContext)
        val locationRepo = LocationRepositoryImpl(db.locationQueries, EmptyCoroutineContext)
        val cellRepo = CellRepositoryImpl(db.cellQueries, EmptyCoroutineContext)
        val submissionRepo = FccSubmissionRepositoryImpl(db.fccSubmissionQueries, EmptyCoroutineContext)

        val localStore = RepositoryBackedMeasurementSyncLocalStore(
            measurementRepository = measurementRepo,
            uploadDownloadDataRepository = uploadRepo,
            latencyDataRepository = latencyRepo,
            locationRepository = locationRepo,
            cellRepository = cellRepo,
            fccSubmissionRepository = submissionRepo,
        )

        val now = Clock.System.now()
        val groupId = UUID.randomUUID().toString()
        val measurementId = UUID.randomUUID().toString()

        measurementRepo.upsert(
            Measurement(
                id = measurementId,
                groupId = groupId,
                deviceId = deviceId,
                type = "latency",
                timestamp = now,
                connectionType = NetworkConnectionType.CELLULAR,
                cellularDataEnabled = true,
            )
        )
        latencyRepo.upsert(
            LatencyData(
                id = UUID.randomUUID().toString(),
                measurementId = measurementId,
                rtt = 25,
                jitter = 2,
                sent = 10,
                received = 10,
            )
        )
        submissionRepo.upsert(
            FccSubmission(
                id = groupId,
                deviceId = deviceId,
                provider = "test-provider",
                submitted = false,
            )
        )

        val useCase = MeasurementSyncUseCase(
            localStore = localStore,
            remoteDataSource = remote,
            tcpTupleProvider = object : TcpTupleProvider {
                override suspend fun getPublicTcpTuple(): TcpTuple =
                    TcpTuple(
                        remoteAddress = "203.0.113.10",
                        remotePort = 4242,
                        timestamp = now.toEpochMilliseconds(),
                    )
            },
            clock = object : Clock {
                override fun now(): Instant = now
            },
        )

        val measurementReport = useCase.syncMeasurements()
        assertEquals(1, measurementReport.attempted)
        assertEquals(1, measurementReport.uploaded)

        val submissionReport = useCase.syncFccSubmissions()
        assertEquals(1, submissionReport.attempted)
        assertEquals(1, submissionReport.uploaded)

        val syncedMeasurement = measurementRepo.getById(measurementId)
        val syncedSubmission = submissionRepo.getById(groupId)
        assertNotNull(syncedMeasurement)
        assertNotNull(syncedSubmission)
        assertNotNull(syncedMeasurement.uploadTime)
        assertNotNull(syncedSubmission.uploadTime)
        Unit
    }
}
