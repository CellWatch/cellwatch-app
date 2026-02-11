package edu.gatech.cc.cellwatch.data.sync

import edu.gatech.cc.cellwatch.domain.model.Cell
import edu.gatech.cc.cellwatch.domain.model.FccSubmission
import edu.gatech.cc.cellwatch.domain.model.LatencyData
import edu.gatech.cc.cellwatch.domain.model.Location
import edu.gatech.cc.cellwatch.domain.model.Measurement
import edu.gatech.cc.cellwatch.domain.model.UploadDownloadData
import edu.gatech.cc.cellwatch.domain.repo.CellRepository
import edu.gatech.cc.cellwatch.domain.repo.FccSubmissionRepository
import edu.gatech.cc.cellwatch.domain.repo.LatencyDataRepository
import edu.gatech.cc.cellwatch.domain.repo.LocationRepository
import edu.gatech.cc.cellwatch.domain.repo.MeasurementRepository
import edu.gatech.cc.cellwatch.domain.repo.UploadDownloadDataRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class RepositoryBackedMeasurementSyncLocalStoreTest {

    @Test
    fun getUnsyncedMeasurements_hydratesChildData() = runBlocking {
        val measurement = Measurement(id = "m1", type = "download")
        val measurementRepo = FakeMeasurementRepository(unsynced = listOf(measurement))
        val uploadRepo = FakeUploadDownloadDataRepository(
            byMeasurementId = mapOf("m1" to listOf(UploadDownloadData(id = "ud1", measurementId = "m1", bytes = 100))),
        )
        val latencyRepo = FakeLatencyDataRepository(
            byMeasurementId = mapOf("m1" to listOf(LatencyData(id = "ld1", measurementId = "m1", rtt = 30))),
        )
        val locationRepo = FakeLocationRepository(
            byMeasurementId = mapOf("m1" to listOf(Location(id = "loc1", lat = 1.0, lon = 2.0, measurementId = "m1"))),
        )
        val cellRepo = FakeCellRepository(
            byMeasurementId = mapOf(
                "m1" to listOf(
                    Cell(
                        id = "c1",
                        timestamp = null,
                        cellId = null,
                        physicalCellId = null,
                        cellConnection = null,
                        networkGeneration = null,
                        networkSubtype = null,
                        signalStrength = -95,
                        rssi = null,
                        rsrp = null,
                        rsrq = null,
                        sinr = null,
                        csiRsrp = null,
                        csiRsrq = null,
                        csiSinr = null,
                        cqi = null,
                        spectrumBand = null,
                        spectrumBandwidth = null,
                        arfcn = null,
                        measurementId = "m1",
                        createdOn = null,
                        updatedOn = null,
                    )
                )
            ),
        )
        val submissionRepo = FakeFccSubmissionRepository()

        val store = RepositoryBackedMeasurementSyncLocalStore(
            measurementRepository = measurementRepo,
            uploadDownloadDataRepository = uploadRepo,
            latencyDataRepository = latencyRepo,
            locationRepository = locationRepo,
            cellRepository = cellRepo,
            fccSubmissionRepository = submissionRepo,
        )

        val result = store.getUnsyncedMeasurements()

        assertEquals(1, result.size)
        assertEquals(100, result.first().uploadDownloadData?.bytes)
        assertEquals(30, result.first().latencyData?.rtt)
        assertEquals(1, result.first().locations?.size)
        assertEquals(1, result.first().cells?.size)
    }

    @Test
    fun markUploaded_delegatesToRepositories() = runBlocking {
        val measurementRepo = FakeMeasurementRepository()
        val submissionRepo = FakeFccSubmissionRepository()

        val store = RepositoryBackedMeasurementSyncLocalStore(
            measurementRepository = measurementRepo,
            uploadDownloadDataRepository = FakeUploadDownloadDataRepository(),
            latencyDataRepository = FakeLatencyDataRepository(),
            locationRepository = FakeLocationRepository(),
            cellRepository = FakeCellRepository(),
            fccSubmissionRepository = submissionRepo,
        )

        val t = Instant.fromEpochMilliseconds(1_710_000_000_000L)
        store.markMeasurementUploaded("m1", t)
        store.markSubmissionUploaded("s1", t)

        assertEquals("m1", measurementRepo.lastMarkedId)
        assertEquals(t, measurementRepo.lastMarkedTime)
        assertEquals("s1", submissionRepo.lastMarkedId)
        assertEquals(t, submissionRepo.lastMarkedTime)
    }

    @Test
    fun getUnsyncedSubmissions_returnsRepositoryData() = runBlocking {
        val submission = FccSubmission(id = "s1", submitted = false)
        val store = RepositoryBackedMeasurementSyncLocalStore(
            measurementRepository = FakeMeasurementRepository(),
            uploadDownloadDataRepository = FakeUploadDownloadDataRepository(),
            latencyDataRepository = FakeLatencyDataRepository(),
            locationRepository = FakeLocationRepository(),
            cellRepository = FakeCellRepository(),
            fccSubmissionRepository = FakeFccSubmissionRepository(unsynced = listOf(submission)),
        )

        val result = store.getUnsyncedSubmissions()
        assertEquals(1, result.size)
        assertEquals("s1", result.first().id)
        assertNotNull(result.first())
        Unit
    }
}

private class FakeMeasurementRepository(
    private val unsynced: List<Measurement> = emptyList(),
) : MeasurementRepository {
    var lastMarkedId: String? = null
    var lastMarkedTime: Instant? = null

    override suspend fun upsert(measurement: Measurement) = Unit
    override suspend fun delete(id: String) = Unit
    override suspend fun getById(id: String): Measurement? = null
    override suspend fun getByGroupId(groupId: String): List<Measurement> = emptyList()
    override suspend fun getUnsynced(): List<Measurement> = unsynced
    override suspend fun markUploaded(id: String, uploadedAt: Instant) {
        lastMarkedId = id
        lastMarkedTime = uploadedAt
    }
    override fun observeByGroupId(groupId: String): Flow<List<Measurement>> = emptyFlow()
}

private class FakeUploadDownloadDataRepository(
    private val byMeasurementId: Map<String, List<UploadDownloadData>> = emptyMap(),
) : UploadDownloadDataRepository {
    override suspend fun upsert(uploadDownloadData: UploadDownloadData) = Unit
    override suspend fun delete(id: String) = Unit
    override suspend fun deleteByMeasurementId(measurementId: String) = Unit
    override suspend fun getByMeasurementId(measurementId: String): List<UploadDownloadData> =
        byMeasurementId[measurementId].orEmpty()
    override fun observeByMeasurementId(measurementId: String): Flow<List<UploadDownloadData>> = emptyFlow()
}

private class FakeLatencyDataRepository(
    private val byMeasurementId: Map<String, List<LatencyData>> = emptyMap(),
) : LatencyDataRepository {
    override suspend fun upsert(latencyData: LatencyData) = Unit
    override suspend fun delete(id: String) = Unit
    override suspend fun deleteByMeasurementId(measurementId: String) = Unit
    override suspend fun getByMeasurementId(measurementId: String): List<LatencyData> =
        byMeasurementId[measurementId].orEmpty()
    override fun observeByMeasurementId(measurementId: String): Flow<List<LatencyData>> = emptyFlow()
}

private class FakeLocationRepository(
    private val byMeasurementId: Map<String, List<Location>> = emptyMap(),
) : LocationRepository {
    override suspend fun upsert(location: Location) = Unit
    override suspend fun delete(id: String) = Unit
    override suspend fun deleteByMeasurementId(measurementId: String) = Unit
    override suspend fun getByMeasurementId(measurementId: String): List<Location> =
        byMeasurementId[measurementId].orEmpty()
    override fun observeByMeasurementId(measurementId: String): Flow<List<Location>> = emptyFlow()
}

private class FakeCellRepository(
    private val byMeasurementId: Map<String, List<Cell>> = emptyMap(),
) : CellRepository {
    override suspend fun upsert(cell: Cell) = Unit
    override suspend fun delete(id: String) = Unit
    override suspend fun getById(id: String): Cell? = null
    override suspend fun getByMeasurement(measurementId: String): List<Cell> =
        byMeasurementId[measurementId].orEmpty()
    override fun observeByMeasurement(measurementId: String): Flow<List<Cell>> = emptyFlow()
}

private class FakeFccSubmissionRepository(
    private val unsynced: List<FccSubmission> = emptyList(),
) : FccSubmissionRepository {
    var lastMarkedId: String? = null
    var lastMarkedTime: Instant? = null

    override suspend fun upsert(submission: FccSubmission) = Unit
    override suspend fun delete(id: String) = Unit
    override suspend fun getById(id: String): FccSubmission? = null
    override suspend fun getUnsubmitted(): List<FccSubmission> = emptyList()
    override suspend fun getUnsynced(): List<FccSubmission> = unsynced
    override suspend fun markUploaded(id: String, uploadedAt: Instant) {
        lastMarkedId = id
        lastMarkedTime = uploadedAt
    }
    override fun observeUnsubmitted(): Flow<List<FccSubmission>> = emptyFlow()
}
