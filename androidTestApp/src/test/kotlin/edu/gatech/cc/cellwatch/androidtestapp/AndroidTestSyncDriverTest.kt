package edu.gatech.cc.cellwatch.androidtestapp

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import edu.gatech.cc.cellwatch.androidtestapp.sync.AndroidTestSyncDriver
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.UUID
import kotlin.coroutines.EmptyCoroutineContext

@RunWith(RobolectricTestRunner::class)
class AndroidTestSyncDriverTest {

    private var driver: AndroidSqliteDriver? = null

    @After
    fun tearDown() {
        driver?.close()
    }

    @Test
    fun runMapStartSync_recordsPartialAndDuplicateStyleReport() = runBlocking {
        val (flow, _, _, _) = buildFlow(
            service = ConfigurableMeasurementSyncService(
                mapReport = SyncAllReport(
                    measurements = SyncReport(
                        attempted = 3,
                        uploaded = 1,
                        markedUploaded = 1,
                        networkErrors = 1,
                    ),
                    submissions = SyncReport(
                        attempted = 2,
                        uploaded = 1,
                        unexpectedErrors = 1,
                    ),
                ),
            )
        )
        val syncDriver = AndroidTestSyncDriver(flow)

        val report = syncDriver.runMapStartSync()

        assertNotNull(report)
        assertEquals(1, report!!.measurements.markedUploaded)
        assertEquals(1, report.measurements.networkErrors)
        assertEquals(1, report.submissions.unexpectedErrors)
        assertNull(syncDriver.state.value.lastError)
    }

    @Test
    fun runMapStartSync_recordsError_whenSyncThrows() = runBlocking {
        val (flow, _, _, _) = buildFlow(
            service = ConfigurableMeasurementSyncService(
                mapException = IllegalStateException("network down"),
            )
        )
        val syncDriver = AndroidTestSyncDriver(flow)

        val report = syncDriver.runMapStartSync()

        assertNull(report)
        assertEquals("network down", syncDriver.state.value.lastError)
    }

    @Test
    fun runMapStartSync_recordsTupleBlockedSubmissionReport() = runBlocking {
        val (flow, _, _, _) = buildFlow(
            service = ConfigurableMeasurementSyncService(
                mapReport = SyncAllReport(
                    measurements = SyncReport(attempted = 1, uploaded = 1),
                    submissions = SyncReport(
                        attempted = 1,
                        blockedBeforeUpload = true,
                        unexpectedErrors = 1,
                    ),
                ),
            )
        )
        val syncDriver = AndroidTestSyncDriver(flow)

        val report = syncDriver.runMapStartSync()

        assertNotNull(report)
        assertEquals(true, report!!.submissions.blockedBeforeUpload)
        assertEquals(1, report.submissions.unexpectedErrors)
    }

    @Test
    fun runMeasurementCompleteSync_recordsError_whenSyncThrows() = runBlocking {
        val (flow, _, _, group) = buildFlow(
            service = ConfigurableMeasurementSyncService(
                mapException = IllegalStateException("tuple fetch failed"),
            )
        )
        val syncDriver = AndroidTestSyncDriver(flow)

        val uploadTime = syncDriver.runMeasurementCompleteSync(group)

        assertNull(uploadTime)
        assertEquals("tuple fetch failed", syncDriver.state.value.lastError)
    }

    private suspend fun buildFlow(
        service: ConfigurableMeasurementSyncService,
    ): BuildResult {
        val context: Context = ApplicationProvider.getApplicationContext()
        val sqlDriver = AndroidSqliteDriver(
            schema = CellwatchDatabase.Schema,
            context = context,
            name = null,
        )
        driver = sqlDriver
        val db = CellwatchDatabase(sqlDriver)
        val measurementRepo = MeasurementRepositoryImpl(db.measurementQueries, EmptyCoroutineContext)
        val submissionRepo = FccSubmissionRepositoryImpl(db.fccSubmissionQueries, EmptyCoroutineContext)

        val groupId = UUID.randomUUID().toString()
        val now = Clock.System.now()
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
                rtt = 8,
                jitter = 1,
                sent = 5,
                received = 5,
            ),
        )
        val submission = FccSubmission(
            id = groupId,
            submitted = false,
            uploadTime = now,
        )
        measurementRepo.upsert(measurement)
        submissionRepo.upsert(submission)

        val flow = LegacySharedSyncFlow(
            syncService = service,
            measurementRepository = measurementRepo,
            submissionRepository = submissionRepo,
        )
        return BuildResult(
            flow = flow,
            measurementRepository = measurementRepo,
            submissionRepository = submissionRepo,
            group = MeasurementGroup(
                latency = measurement,
                download = null,
                upload = null,
                submission = submission,
                id = groupId,
            ),
        )
    }

    private data class BuildResult(
        val flow: LegacySharedSyncFlow,
        val measurementRepository: MeasurementRepositoryImpl,
        val submissionRepository: FccSubmissionRepositoryImpl,
        val group: MeasurementGroup,
    )
}

private class ConfigurableMeasurementSyncService(
    private val mapReport: SyncAllReport = SyncAllReport(
        measurements = SyncReport(),
        submissions = SyncReport(),
    ),
    private val mapException: Throwable? = null,
) : MeasurementSyncService {

    override suspend fun syncMeasurements(): SyncReport = SyncReport()

    override suspend fun syncFccSubmissions(): SyncReport = SyncReport()

    override suspend fun syncAll(): SyncAllReport {
        if (mapException != null) throw mapException
        return mapReport
    }
}
