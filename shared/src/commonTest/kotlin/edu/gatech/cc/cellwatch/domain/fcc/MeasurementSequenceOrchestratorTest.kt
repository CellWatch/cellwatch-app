package edu.gatech.cc.cellwatch.domain.fcc

import edu.gatech.cc.cellwatch.domain.model.CollectionMode
import edu.gatech.cc.cellwatch.domain.model.FccSubmission
import edu.gatech.cc.cellwatch.domain.model.LatencyData
import edu.gatech.cc.cellwatch.domain.model.Measurement
import edu.gatech.cc.cellwatch.domain.model.NetworkConnectionType
import edu.gatech.cc.cellwatch.domain.model.UploadDownloadData
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class MeasurementSequenceOrchestratorTest {
    @Test
    fun `run executes latency download upload sequence and persists each measurement`() = runBlocking {
        val recorder = Recorder()
        val orchestrator = MeasurementSequenceOrchestrator(
            serverPairProvider = FakeServerPairProvider(),
            measurementExecutor = FakeMeasurementExecutor(recorder),
            resultStore = FakeMeasurementResultStore(recorder),
            submissionContextFactory = FakeSubmissionContextFactory(),
        )

        val outcome = orchestrator.run(
            MeasurementSequenceRequest(
                groupId = "group-1",
                inVehicle = false,
                mode = CollectionMode.TESTING,
                measurementId = "mid-1",
            ),
        )

        assertEquals("throughput.example", outcome.throughputServerMachine)
        assertEquals("latency.example", outcome.latencyServerMachine)
        assertNull(outcome.group.submission)
        assertEquals(
            listOf(
                "exec-latency",
                "store-measurement-latency-1",
                "exec-throughput-download",
                "store-measurement-download-1",
                "exec-throughput-upload",
                "store-measurement-upload-1",
            ),
            recorder.events,
        )
        assertEquals(listOf<String?>("mid-1", "mid-1", "mid-1"), recorder.measurementIdsSeen)
    }

    @Test
    fun `run builds and stores FCC submission when eligible`() = runBlocking {
        val recorder = Recorder()
        val orchestrator = MeasurementSequenceOrchestrator(
            serverPairProvider = FakeServerPairProvider(),
            measurementExecutor = FakeMeasurementExecutor(recorder),
            resultStore = FakeMeasurementResultStore(recorder),
            submissionContextFactory = FakeSubmissionContextFactory(),
        )

        val outcome = orchestrator.run(
            MeasurementSequenceRequest(
                groupId = "group-2",
                inVehicle = true,
                mode = CollectionMode.FCC_CHALLENGE,
                measurementId = null,
            ),
        )

        val submission = outcome.group.submission
        assertNotNull(submission)
        assertEquals("group-2", submission.id)
        assertEquals(true, submission.inVehicle)
        assertEquals("download-device", submission.deviceId)
        assertEquals("latency-manufacturer", submission.deviceManufacturer)
        assertEquals("download-model", submission.deviceModel)
        assertEquals("Android 14", submission.deviceOsName)
        assertEquals("CellWatch Shared", submission.appName)
        assertEquals("2.0.0-test", submission.appVersion)
        assertEquals("test-carrier", submission.provider)
        assertEquals("310", submission.simCountryCode)
        assertEquals("260", submission.simNetworkCode)
        assertEquals("311", submission.netCountryCode)
        assertEquals("480", submission.netNetworkCode)
        assertEquals(
            listOf(
                "exec-latency",
                "store-measurement-latency-1",
                "exec-throughput-download",
                "store-measurement-download-1",
                "exec-throughput-upload",
                "store-measurement-upload-1",
                "store-submission-group-2",
            ),
            recorder.events,
        )
    }

    @Test
    fun `run skips FCC submission when any measurement is wifi`() = runBlocking {
        val recorder = Recorder()
        val executor = FakeMeasurementExecutor(
            recorder = recorder,
            latencyConnectionType = NetworkConnectionType.WIFI,
        )
        val orchestrator = MeasurementSequenceOrchestrator(
            serverPairProvider = FakeServerPairProvider(),
            measurementExecutor = executor,
            resultStore = FakeMeasurementResultStore(recorder),
            submissionContextFactory = FakeSubmissionContextFactory(),
        )

        val outcome = orchestrator.run(
            MeasurementSequenceRequest(
                groupId = "group-3",
                inVehicle = false,
                mode = CollectionMode.FCC_CHALLENGE,
            ),
        )

        assertNull(outcome.group.submission)
        assertEquals(
            listOf(
                "exec-latency",
                "store-measurement-latency-1",
                "exec-throughput-download",
                "store-measurement-download-1",
                "exec-throughput-upload",
                "store-measurement-upload-1",
            ),
            recorder.events,
        )
    }

    @Test
    fun `run emits stage callbacks in deterministic order`() = runBlocking {
        val recorder = Recorder()
        val stages = mutableListOf<MeasurementSequenceStage>()
        val orchestrator = MeasurementSequenceOrchestrator(
            serverPairProvider = FakeServerPairProvider(),
            measurementExecutor = FakeMeasurementExecutor(recorder),
            resultStore = FakeMeasurementResultStore(recorder),
            submissionContextFactory = FakeSubmissionContextFactory(),
            progressListener = MeasurementSequenceProgressListener { stage ->
                stages += stage
            },
        )

        orchestrator.run(
            MeasurementSequenceRequest(
                groupId = "group-4",
                inVehicle = false,
                mode = CollectionMode.TESTING,
            ),
        )

        assertEquals(
            listOf(
                MeasurementSequenceStage.STARTED,
                MeasurementSequenceStage.LOCATE,
                MeasurementSequenceStage.LATENCY,
                MeasurementSequenceStage.DOWNLOAD,
                MeasurementSequenceStage.UPLOAD,
                MeasurementSequenceStage.DONE,
            ),
            stages,
        )
    }
}

private class Recorder {
    val events = mutableListOf<String>()
    val measurementIdsSeen = mutableListOf<String?>()
}

private class FakeServerPairProvider : MsakServerPairProvider {
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

private class FakeMeasurementExecutor(
    private val recorder: Recorder,
    private val latencyConnectionType: NetworkConnectionType = NetworkConnectionType.CELLULAR,
) : MeasurementExecutor {
    override suspend fun runLatency(
        server: MsakServerEndpoint,
        groupId: String,
        measurementId: String?,
    ): Measurement {
        recorder.events += "exec-latency"
        recorder.measurementIdsSeen += measurementId
        return Measurement(
            id = "latency-1",
            groupId = groupId,
            type = "latency",
            connectionType = latencyConnectionType,
            cellularDataEnabled = true,
            deviceManufacturer = "latency-manufacturer",
            deviceOsVersion = "14",
            simMcc = "310",
            netMnc = "480",
            latencyData = LatencyData(
                id = "latency-data-1",
                measurementId = "latency-1",
                rtt = 10,
            ),
        )
    }

    override suspend fun runThroughput(
        server: MsakServerEndpoint,
        direction: ThroughputDirection,
        groupId: String,
        measurementId: String?,
    ): Measurement {
        recorder.events += "exec-throughput-${direction.name.lowercase()}"
        recorder.measurementIdsSeen += measurementId
        val id = if (direction == ThroughputDirection.DOWNLOAD) "download-1" else "upload-1"
        return Measurement(
            id = id,
            groupId = groupId,
            type = direction.name.lowercase(),
            connectionType = NetworkConnectionType.CELLULAR,
            cellularDataEnabled = true,
            deviceId = if (direction == ThroughputDirection.DOWNLOAD) "download-device" else null,
            deviceModel = if (direction == ThroughputDirection.DOWNLOAD) "download-model" else null,
            appName = if (direction == ThroughputDirection.DOWNLOAD) "CellWatch Shared" else null,
            simMnc = if (direction == ThroughputDirection.DOWNLOAD) "260" else null,
            netMcc = if (direction == ThroughputDirection.DOWNLOAD) "311" else null,
            uploadDownloadData = UploadDownloadData(
                id = "ud-$id",
                measurementId = id,
                bytes = 100,
            ),
        )
    }
}

private class FakeMeasurementResultStore(
    private val recorder: Recorder,
) : MeasurementResultStore {
    override suspend fun insertMeasurement(measurement: Measurement) {
        recorder.events += "store-measurement-${measurement.id}"
    }

    override suspend fun insertFccSubmission(submission: FccSubmission) {
        recorder.events += "store-submission-${submission.id}"
    }
}

private class FakeSubmissionContextFactory : FccSubmissionContextFactory {
    override fun create(
        groupId: String,
        inVehicle: Boolean,
        metadata: FccSubmissionMetadataSnapshot,
    ): FccSubmissionBuildContext {
        return FccSubmissionBuildContext(
            groupId = groupId,
            deviceTimestamp = Instant.fromEpochMilliseconds(1_710_000_123_000L),
            inVehicle = inVehicle,
            externalAntenna = false,
            deviceType = "Android",
            deviceOsName = "Android ${metadata.deviceOsVersion}",
            appVersion = "2.0.0-test",
            provider = "test-carrier",
            contactName = "Test User",
            contactEmail = "test@example.com",
            contactPhone = "555-0100",
        )
    }
}
