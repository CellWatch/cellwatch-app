package edu.gatech.cc.cellwatch.androidtestapp

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import edu.gatech.cc.cellwatch.androidtestapp.sync.LegacySharedSyncFlow
import edu.gatech.cc.cellwatch.data.repo.FccSubmissionRepositoryImpl
import edu.gatech.cc.cellwatch.data.repo.MeasurementRepositoryImpl
import edu.gatech.cc.cellwatch.db.CellwatchDatabase
import edu.gatech.cc.cellwatch.domain.model.FccSubmission
import edu.gatech.cc.cellwatch.domain.model.LatencyData
import edu.gatech.cc.cellwatch.domain.model.Measurement
import edu.gatech.cc.cellwatch.domain.model.MeasurementGroup
import edu.gatech.cc.cellwatch.domain.model.NetworkConnectionType
import edu.gatech.cc.cellwatch.domain.sync.MeasurementSyncService
import edu.gatech.cc.cellwatch.domain.sync.SyncAllReport
import edu.gatech.cc.cellwatch.domain.sync.SyncReport
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.UUID
import kotlin.coroutines.EmptyCoroutineContext

@RunWith(RobolectricTestRunner::class)
class LegacySharedSyncFlowTest {

    private lateinit var driver: AndroidSqliteDriver
    private lateinit var db: CellwatchDatabase
    private lateinit var measurementRepo: MeasurementRepositoryImpl
    private lateinit var submissionRepo: FccSubmissionRepositoryImpl

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        driver = AndroidSqliteDriver(
            schema = CellwatchDatabase.Schema,
            context = context,
            name = null,
        )
        db = CellwatchDatabase(driver)
        measurementRepo = MeasurementRepositoryImpl(db.measurementQueries, EmptyCoroutineContext)
        submissionRepo = FccSubmissionRepositoryImpl(db.fccSubmissionQueries, EmptyCoroutineContext)
    }

    @After
    fun tearDown() {
        if (this::driver.isInitialized) {
            driver.close()
        }
    }

    @Test
    fun mapStartSync_callsSharedSyncService() = runBlocking {
        val service = FakeMeasurementSyncService()
        val flow = LegacySharedSyncFlow(service, measurementRepo, submissionRepo)

        flow.onMapStartSync()

        assertEquals(1, service.syncAllCalls)
    }

    @Test
    fun measurementCompleteSync_returnsMaxUploadTime_whenBothUploaded() = runBlocking {
        val service = FakeMeasurementSyncService()
        val flow = LegacySharedSyncFlow(service, measurementRepo, submissionRepo)

        val now = Clock.System.now()
        val later = Instant.fromEpochMilliseconds(now.toEpochMilliseconds() + 1_000)
        val groupId = UUID.randomUUID().toString()
        val measurement = Measurement(
            id = UUID.randomUUID().toString(),
            groupId = groupId,
            type = "latency",
            timestamp = now,
            connectionType = NetworkConnectionType.CELLULAR,
            cellularDataEnabled = true,
            uploadTime = now,
            latencyData = LatencyData(
                id = UUID.randomUUID().toString(),
                measurementId = UUID.randomUUID().toString(),
                rtt = 10,
                jitter = 1,
                sent = 5,
                received = 5,
            ),
        )
        val submission = FccSubmission(
            id = groupId,
            submitted = false,
            uploadTime = later,
        )
        measurementRepo.upsert(measurement)
        submissionRepo.upsert(submission)

        val uploadTime = flow.onMeasurementCompleteSync(
            MeasurementGroup(
                latency = measurement,
                download = null,
                upload = null,
                submission = submission,
                id = groupId,
            )
        )

        assertEquals(1, service.syncAllCalls)
        assertEquals(later, uploadTime)
    }

    @Test
    fun resolveUploadTime_returnsNull_whenSubmissionNotYetUploaded() = runBlocking {
        val flow = LegacySharedSyncFlow(FakeMeasurementSyncService(), measurementRepo, submissionRepo)

        val now = Clock.System.now()
        val groupId = UUID.randomUUID().toString()
        val measurement = Measurement(
            id = UUID.randomUUID().toString(),
            groupId = groupId,
            type = "latency",
            timestamp = now,
            connectionType = NetworkConnectionType.CELLULAR,
            cellularDataEnabled = true,
            uploadTime = now,
            latencyData = LatencyData(
                id = UUID.randomUUID().toString(),
                measurementId = UUID.randomUUID().toString(),
                rtt = 10,
                jitter = 1,
                sent = 5,
                received = 5,
            ),
        )
        val submission = FccSubmission(
            id = groupId,
            submitted = false,
            uploadTime = null,
        )
        measurementRepo.upsert(measurement)
        submissionRepo.upsert(submission)

        val uploadTime = flow.resolveUploadTime(
            MeasurementGroup(
                latency = measurement,
                download = null,
                upload = null,
                submission = submission,
                id = groupId,
            )
        )
        assertNull(uploadTime)
    }
}

private class FakeMeasurementSyncService : MeasurementSyncService {
    var syncAllCalls: Int = 0

    override suspend fun syncMeasurements(): SyncReport = SyncReport()

    override suspend fun syncFccSubmissions(): SyncReport = SyncReport()

    override suspend fun syncAll(): SyncAllReport {
        syncAllCalls += 1
        return SyncAllReport(
            measurements = SyncReport(),
            submissions = SyncReport(),
        )
    }
}
