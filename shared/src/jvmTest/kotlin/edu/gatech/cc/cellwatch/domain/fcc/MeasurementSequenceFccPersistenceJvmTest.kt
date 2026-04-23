package edu.gatech.cc.cellwatch.domain.fcc

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import edu.gatech.cc.cellwatch.data.repo.CellRepositoryImpl
import edu.gatech.cc.cellwatch.data.repo.FccSubmissionRepositoryImpl
import edu.gatech.cc.cellwatch.data.repo.LatencyDataRepositoryImpl
import edu.gatech.cc.cellwatch.data.repo.LocationRepositoryImpl
import edu.gatech.cc.cellwatch.data.repo.MeasurementRepositoryImpl
import edu.gatech.cc.cellwatch.data.repo.UploadDownloadDataRepositoryImpl
import edu.gatech.cc.cellwatch.db.CellwatchDatabase
import edu.gatech.cc.cellwatch.domain.model.Cell
import edu.gatech.cc.cellwatch.domain.model.CollectionMode
import edu.gatech.cc.cellwatch.domain.model.LatencyData
import edu.gatech.cc.cellwatch.domain.model.Location
import edu.gatech.cc.cellwatch.domain.model.Measurement
import edu.gatech.cc.cellwatch.domain.model.NetworkConnectionType
import edu.gatech.cc.cellwatch.domain.model.UploadDownloadData
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class MeasurementSequenceFccPersistenceJvmTest {
    private lateinit var driver: JdbcSqliteDriver
    private lateinit var db: CellwatchDatabase

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        CellwatchDatabase.Schema.create(driver)
        db = CellwatchDatabase(driver)
    }

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    @Test
    fun `repository backed sequence stores corrected FCC submission fields and location samples`() = runBlocking {
        val measurementRepo = MeasurementRepositoryImpl(db.measurementQueries, EmptyCoroutineContext)
        val latencyRepo = LatencyDataRepositoryImpl(db.latencyDataQueries, EmptyCoroutineContext)
        val uploadRepo = UploadDownloadDataRepositoryImpl(db.uploadDownloadDataQueries, EmptyCoroutineContext)
        val submissionRepo = FccSubmissionRepositoryImpl(db.fccSubmissionQueries, EmptyCoroutineContext)
        val locationRepo = LocationRepositoryImpl(db.locationQueries, EmptyCoroutineContext)
        val cellRepo = CellRepositoryImpl(db.cellQueries, EmptyCoroutineContext)

        val orchestrator = MeasurementSequenceOrchestrator(
            serverPairProvider = object : MsakServerPairProvider {
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
            },
            measurementExecutor = PersistenceFakeMeasurementExecutor(),
            resultStore = RepositoryBackedMeasurementResultStore(
                measurementRepository = measurementRepo,
                latencyDataRepository = latencyRepo,
                uploadDownloadDataRepository = uploadRepo,
                submissionRepository = submissionRepo,
                locationRepository = locationRepo,
                cellRepository = cellRepo,
            ),
            submissionContextFactory = object : FccSubmissionContextFactory {
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
                        submissionProfile = request.submissionProfile,
                    )
                }
            },
        )

        val groupId = "group-fcc-persist"
        val outcome = orchestrator.run(
            MeasurementSequenceRequest(
                groupId = groupId,
                inVehicle = false,
                mode = CollectionMode.FCC_CHALLENGE,
                submissionProfile = FccSubmissionProfile(
                    appName = "CellWatch",
                    appVersion = "1.0 (42)",
                    deviceId = "device-android-123",
                    provider = "Carrier From App",
                    contactName = "Ada Lovelace",
                    contactEmail = "ada@example.com",
                    contactPhone = "404-111-2222",
                ),
            ),
        )

        val submission = assertNotNull(submissionRepo.getById(groupId))
        assertNotNull(outcome.group.submission)
        assertEquals("CellWatch", submission.appName)
        assertEquals("1.0 (42)", submission.appVersion)
        assertEquals("device-android-123", submission.deviceId)
        assertEquals("Carrier From App", submission.provider)
        assertEquals("Ada Lovelace", submission.contactName)
        assertEquals("ada@example.com", submission.contactEmail)
        assertEquals("404-111-2222", submission.contactPhone)

        val storedLatency = assertNotNull(measurementRepo.getById("latency-1"))
        val storedLocations = locationRepo.getByMeasurementId(storedLatency.id)
        val storedCells = cellRepo.getByMeasurement(storedLatency.id)
        assertEquals(1, storedLocations.size)
        assertEquals(33.7488, storedLocations.first().lat)
        assertEquals(-84.3880, storedLocations.first().lon)
        assertEquals(1, storedCells.size)
        assertEquals(12345L, storedCells.first().cellId)
    }
}

private class PersistenceFakeMeasurementExecutor : MeasurementExecutor {
    override suspend fun runLatency(
        server: MsakServerEndpoint,
        groupId: String,
        measurementId: String?,
    ): Measurement {
        return Measurement(
            id = "latency-1",
            groupId = groupId,
            type = "latency",
            connectionType = NetworkConnectionType.CELLULAR,
            cellularDataEnabled = true,
            deviceManufacturer = "Google",
            deviceModel = "Pixel",
            deviceOsName = "Android",
            deviceOsVersion = "14",
            locations = listOf(
                Location(
                    id = "location-1",
                    timestamp = Instant.fromEpochMilliseconds(1_710_000_000_100L),
                    lat = 33.7488,
                    lon = -84.3880,
                ),
            ),
            cells = listOf(
                Cell(
                    id = "cell-1",
                    timestamp = Instant.fromEpochMilliseconds(1_710_000_000_200L),
                    cellId = 12345L,
                    physicalCellId = 321,
                    cellConnection = 1,
                    networkGeneration = "5G",
                    networkSubtype = "NR",
                    signalStrength = -95,
                    rssi = -60,
                    rsrp = -98,
                    rsrq = -11,
                    sinr = 18,
                    csiRsrp = null,
                    csiRsrq = null,
                    csiSinr = null,
                    cqi = null,
                    spectrumBand = "n41",
                    spectrumBandwidth = 100.0f,
                    arfcn = 635334,
                    measurementId = "latency-1",
                    createdOn = null,
                    updatedOn = null,
                ),
            ),
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
        val type = direction.name.lowercase()
        val id = "$type-1"
        return Measurement(
            id = id,
            groupId = groupId,
            type = type,
            connectionType = NetworkConnectionType.CELLULAR,
            cellularDataEnabled = true,
            deviceManufacturer = "Google",
            deviceModel = "Pixel",
            deviceOsName = "Android",
            deviceOsVersion = "14",
            uploadDownloadData = UploadDownloadData(
                id = "ud-$id",
                measurementId = id,
                bytes = 1000,
            ),
        )
    }
}
