package edu.gatech.cc.cellwatch.domain.sync

import edu.gatech.cc.cellwatch.domain.fcc.FccSubmissionBuildContext
import edu.gatech.cc.cellwatch.domain.fcc.FccSubmissionContextFactory
import edu.gatech.cc.cellwatch.domain.fcc.FccSubmissionMetadataSnapshot
import edu.gatech.cc.cellwatch.domain.fcc.FccSubmissionProfile
import edu.gatech.cc.cellwatch.domain.fcc.MeasurementExecutor
import edu.gatech.cc.cellwatch.domain.fcc.MeasurementResultStore
import edu.gatech.cc.cellwatch.domain.fcc.MeasurementSequenceOrchestrator
import edu.gatech.cc.cellwatch.domain.fcc.MeasurementSequenceRequest
import edu.gatech.cc.cellwatch.domain.fcc.MsakServerEndpoint
import edu.gatech.cc.cellwatch.domain.fcc.MsakServerPair
import edu.gatech.cc.cellwatch.domain.fcc.MsakServerPairProvider
import edu.gatech.cc.cellwatch.domain.fcc.ThroughputDirection
import edu.gatech.cc.cellwatch.domain.model.CollectionMode
import edu.gatech.cc.cellwatch.domain.model.FccSubmission
import edu.gatech.cc.cellwatch.domain.model.LatencyData
import edu.gatech.cc.cellwatch.domain.model.Measurement
import edu.gatech.cc.cellwatch.domain.model.NetworkConnectionType
import edu.gatech.cc.cellwatch.domain.model.UploadDownloadData
import edu.gatech.cc.cellwatch.domain.repo.FccSubmissionRepository
import edu.gatech.cc.cellwatch.domain.repo.MeasurementRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class MeasurementSequenceSyncOrchestratorTest {

    @Test
    fun run_executesMapStartThenSequenceThenMeasurementCompleteSync() = runBlocking {
        val measurementUploadTime = Instant.fromEpochMilliseconds(1_710_000_005_000L)
        val submissionUploadTime = Instant.fromEpochMilliseconds(1_710_000_009_000L)
        val syncService = SequenceSyncRecordingSyncService(
            report = SyncAllReport(
                measurements = SyncReport(attempted = 1, uploaded = 1),
                submissions = SyncReport(attempted = 1, uploaded = 1),
            ),
        )
        val uploadTriggerUseCase = UploadTriggerUseCase(
            syncService = syncService,
            measurementRepository = StaticMeasurementRepository(
                byId = mapOf(
                    "latency-1" to latencyMeasurement(
                        groupId = "group-1",
                        id = "latency-1",
                        uploadTime = measurementUploadTime,
                    ),
                ),
            ),
            submissionRepository = StaticSubmissionRepository(
                byId = mapOf(
                    "group-1" to FccSubmission(id = "group-1", uploadTime = submissionUploadTime),
                ),
            ),
        )

        val sequenceOrchestrator = MeasurementSequenceOrchestrator(
            serverPairProvider = StaticServerPairProvider(),
            measurementExecutor = StaticMeasurementExecutor(),
            resultStore = NoOpResultStore(),
            submissionContextFactory = StaticSubmissionContextFactory(),
        )
        val orchestrator = MeasurementSequenceSyncOrchestrator(
            sequenceOrchestrator = sequenceOrchestrator,
            uploadTriggerUseCase = uploadTriggerUseCase,
        )

        val outcome = orchestrator.run(
            request = MeasurementSequenceRequest(
                groupId = "group-1",
                inVehicle = false,
                mode = CollectionMode.FCC_CHALLENGE,
            ),
        )

        assertEquals(2, syncService.syncAllCalls)
        assertEquals(1, outcome.mapStartReport.measurements.uploaded)
        assertEquals(1, outcome.mapStartReport.submissions.uploaded)
        assertEquals("throughput.example", outcome.sequenceOutcome.throughputServerMachine)
        assertEquals("latency.example", outcome.sequenceOutcome.latencyServerMachine)
        assertNotNull(outcome.sequenceOutcome.group.submission)
        assertEquals(submissionUploadTime, outcome.measurementCompleteUploadTime)
    }

    @Test
    fun run_whenSequenceFails_stopsBeforeMeasurementCompleteSync() = runBlocking {
        val syncService = SequenceSyncRecordingSyncService(
            report = SyncAllReport(
                measurements = SyncReport(attempted = 1, uploaded = 1),
                submissions = SyncReport(attempted = 1, uploaded = 1),
            ),
        )
        val uploadTriggerUseCase = UploadTriggerUseCase(
            syncService = syncService,
            measurementRepository = StaticMeasurementRepository(byId = emptyMap()),
            submissionRepository = StaticSubmissionRepository(byId = emptyMap()),
        )
        val sequenceOrchestrator = MeasurementSequenceOrchestrator(
            serverPairProvider = StaticServerPairProvider(),
            measurementExecutor = FailingMeasurementExecutor(),
            resultStore = NoOpResultStore(),
            submissionContextFactory = StaticSubmissionContextFactory(),
        )
        val orchestrator = MeasurementSequenceSyncOrchestrator(
            sequenceOrchestrator = sequenceOrchestrator,
            uploadTriggerUseCase = uploadTriggerUseCase,
        )

        assertFailsWith<IllegalStateException> {
            orchestrator.run(
                request = MeasurementSequenceRequest(
                    groupId = "group-fail-sequence",
                    inVehicle = false,
                    mode = CollectionMode.FCC_CHALLENGE,
                ),
            )
        }
        assertEquals(1, syncService.syncAllCalls)
    }

    @Test
    fun run_whenMeasurementCompleteSyncFails_throwsAfterSequence() = runBlocking {
        val syncService = FailingOnCallSyncService(
            report = SyncAllReport(
                measurements = SyncReport(attempted = 1, uploaded = 1),
                submissions = SyncReport(attempted = 1, uploaded = 1),
            ),
            failOnCall = 2,
        )
        val uploadTriggerUseCase = UploadTriggerUseCase(
            syncService = syncService,
            measurementRepository = StaticMeasurementRepository(byId = emptyMap()),
            submissionRepository = StaticSubmissionRepository(byId = emptyMap()),
        )
        val sequenceOrchestrator = MeasurementSequenceOrchestrator(
            serverPairProvider = StaticServerPairProvider(),
            measurementExecutor = StaticMeasurementExecutor(),
            resultStore = NoOpResultStore(),
            submissionContextFactory = StaticSubmissionContextFactory(),
        )
        val orchestrator = MeasurementSequenceSyncOrchestrator(
            sequenceOrchestrator = sequenceOrchestrator,
            uploadTriggerUseCase = uploadTriggerUseCase,
        )

        assertFailsWith<IllegalStateException> {
            orchestrator.run(
                request = MeasurementSequenceRequest(
                    groupId = "group-fail-sync",
                    inVehicle = false,
                    mode = CollectionMode.FCC_CHALLENGE,
                ),
            )
        }
        assertEquals(2, syncService.syncAllCalls)
    }
}

private class StaticServerPairProvider : MsakServerPairProvider {
    override suspend fun chooseServers(): MsakServerPair {
        return MsakServerPair(
            throughputServer = MsakServerEndpoint(
                machine = "throughput.example",
                urls = mapOf("https://throughput.example" to "https://throughput.example"),
            ),
            latencyServer = MsakServerEndpoint(
                machine = "latency.example",
                urls = mapOf("https://latency.example" to "https://latency.example"),
            ),
        )
    }
}

private class StaticMeasurementExecutor : MeasurementExecutor {
    override suspend fun runLatency(
        server: MsakServerEndpoint,
        groupId: String,
        measurementId: String?,
    ): Measurement = latencyMeasurement(
        groupId = groupId,
        id = "latency-1",
        uploadTime = null,
    ).copy(
        deviceManufacturer = "Google",
        deviceOsVersion = "14",
    )

    override suspend fun runThroughput(
        server: MsakServerEndpoint,
        direction: ThroughputDirection,
        groupId: String,
        measurementId: String?,
    ): Measurement {
        val type = direction.name.lowercase()
        return Measurement(
            id = "$type-1",
            groupId = groupId,
            type = type,
            connectionType = NetworkConnectionType.CELLULAR,
            cellularDataEnabled = true,
            deviceModel = "Pixel",
            uploadDownloadData = UploadDownloadData(
                id = "ud-$type-1",
                measurementId = "$type-1",
                bytes = 100,
            ),
        )
    }
}

private class FailingMeasurementExecutor : MeasurementExecutor {
    override suspend fun runLatency(
        server: MsakServerEndpoint,
        groupId: String,
        measurementId: String?,
    ): Measurement {
        throw IllegalStateException("synthetic sequence failure")
    }

    override suspend fun runThroughput(
        server: MsakServerEndpoint,
        direction: ThroughputDirection,
        groupId: String,
        measurementId: String?,
    ): Measurement {
        error("throughput should not run after latency failure")
    }
}

private class NoOpResultStore : MeasurementResultStore {
    override suspend fun insertMeasurement(measurement: Measurement) = Unit
    override suspend fun insertFccSubmission(submission: FccSubmission) = Unit
}

private class StaticSubmissionContextFactory : FccSubmissionContextFactory {
    override fun create(
        request: MeasurementSequenceRequest,
        groupId: String,
        inVehicle: Boolean,
        metadata: FccSubmissionMetadataSnapshot,
    ): FccSubmissionBuildContext {
        return FccSubmissionBuildContext(
            groupId = groupId,
            deviceTimestamp = Instant.fromEpochMilliseconds(1_710_000_000_000L),
            inVehicle = inVehicle,
            externalAntenna = false,
            deviceType = "Android",
            deviceOsName = "Android 14",
            submissionProfile = FccSubmissionProfile(
                appName = "CellWatch",
                appVersion = "1.0-test",
                deviceId = "device-1",
                provider = "test",
                contactName = "Test User",
                contactEmail = "test@example.com",
                contactPhone = "404-111-2222",
            ),
        )
    }
}

private class StaticMeasurementRepository(
    private val byId: Map<String, Measurement>,
) : MeasurementRepository {
    override suspend fun upsert(measurement: Measurement) = Unit
    override suspend fun delete(id: String) = Unit
    override suspend fun getById(id: String): Measurement? = byId[id]
    override suspend fun getByGroupId(groupId: String): List<Measurement> = emptyList()
    override suspend fun getUnsynced(): List<Measurement> = emptyList()
    override suspend fun markUploaded(id: String, uploadedAt: Instant) = Unit
    override fun observeByGroupId(groupId: String): Flow<List<Measurement>> = emptyFlow()
}

private class StaticSubmissionRepository(
    private val byId: Map<String, FccSubmission>,
) : FccSubmissionRepository {
    override suspend fun upsert(submission: FccSubmission) = Unit
    override suspend fun delete(id: String) = Unit
    override suspend fun getById(id: String): FccSubmission? = byId[id]
    override suspend fun getUnsubmitted(): List<FccSubmission> = emptyList()
    override suspend fun getUnsynced(): List<FccSubmission> = emptyList()
    override suspend fun markUploaded(id: String, uploadedAt: Instant) = Unit
    override fun observeUnsubmitted(): Flow<List<FccSubmission>> = emptyFlow()
}

private fun latencyMeasurement(
    groupId: String,
    id: String,
    uploadTime: Instant?,
): Measurement {
    return Measurement(
        id = id,
        groupId = groupId,
        type = "latency",
        connectionType = NetworkConnectionType.CELLULAR,
        cellularDataEnabled = true,
        uploadTime = uploadTime,
        latencyData = LatencyData(
            id = "latency-data-$id",
            measurementId = id,
            rtt = 10,
        ),
    )
}

private class SequenceSyncRecordingSyncService(
    private val report: SyncAllReport,
) : MeasurementSyncService {
    var syncAllCalls: Int = 0

    override suspend fun syncMeasurements(): SyncReport = report.measurements

    override suspend fun syncFccSubmissions(): SyncReport = report.submissions

    override suspend fun syncAll(): SyncAllReport {
        syncAllCalls += 1
        return report
    }
}

private class FailingOnCallSyncService(
    private val report: SyncAllReport,
    private val failOnCall: Int,
) : MeasurementSyncService {
    var syncAllCalls: Int = 0

    override suspend fun syncMeasurements(): SyncReport = report.measurements

    override suspend fun syncFccSubmissions(): SyncReport = report.submissions

    override suspend fun syncAll(): SyncAllReport {
        syncAllCalls += 1
        if (syncAllCalls == failOnCall) {
            throw IllegalStateException("synthetic sync failure on call $syncAllCalls")
        }
        return report
    }
}
