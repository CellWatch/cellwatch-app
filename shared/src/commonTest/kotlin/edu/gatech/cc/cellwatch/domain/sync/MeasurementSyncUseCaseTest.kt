package edu.gatech.cc.cellwatch.domain.sync

import edu.gatech.cc.cellwatch.domain.model.FccSubmission
import edu.gatech.cc.cellwatch.domain.model.Measurement
import edu.gatech.cc.cellwatch.domain.model.TcpTuple
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class MeasurementSyncUseCaseTest {

    @Test
    fun syncMeasurements_marksUploaded_onSuccess() = runBlocking {
        val now = Instant.fromEpochMilliseconds(1_710_000_000_000L)
        val m1 = Measurement(id = "m1", groupId = "g1", type = "latency", timestamp = now)
        val local = FakeLocalStore(measurements = mutableListOf(m1))
        val remote = FakeRemoteDataSource(insertMeasurementMode = InsertMeasurementMode.SUCCESS)

        val report = MeasurementSyncUseCase(local, remote, FakeTcpTupleProvider()).syncMeasurements()

        assertEquals(1, report.attempted)
        assertEquals(1, report.uploaded)
        assertEquals(0, report.networkErrors)
        assertEquals(1, local.markedMeasurementIds.size)
        assertEquals("m1", local.markedMeasurementIds.first())
    }

    @Test
    fun syncMeasurements_marksUploaded_onDuplicate_whenRemoteTimestampMatches() = runBlocking {
        val now = Instant.fromEpochMilliseconds(1_710_000_000_000L)
        val m1 = Measurement(id = "m1", groupId = "g1", type = "latency", timestamp = now)
        val local = FakeLocalStore(measurements = mutableListOf(m1))
        val remote = FakeRemoteDataSource(
            insertMeasurementMode = InsertMeasurementMode.DUPLICATE,
            getMeasurementByIdResult = Result.success(m1),
        )

        val report = MeasurementSyncUseCase(local, remote, FakeTcpTupleProvider()).syncMeasurements()

        assertEquals(1, report.attempted)
        assertEquals(0, report.uploaded)
        assertEquals(1, report.markedUploaded)
        assertEquals(1, local.markedMeasurementIds.size)
    }

    @Test
    fun syncMeasurements_keepsUnsynced_onDuplicate_whenRemoteTimestampMismatch() = runBlocking {
        val localMeasurement = Measurement(
            id = "m1",
            groupId = "g1",
            type = "latency",
            timestamp = Instant.fromEpochMilliseconds(10),
        )
        val remoteMeasurement = localMeasurement.copy(timestamp = Instant.fromEpochMilliseconds(20))
        val local = FakeLocalStore(measurements = mutableListOf(localMeasurement))
        val remote = FakeRemoteDataSource(
            insertMeasurementMode = InsertMeasurementMode.DUPLICATE,
            getMeasurementByIdResult = Result.success(remoteMeasurement),
        )

        val report = MeasurementSyncUseCase(local, remote, FakeTcpTupleProvider()).syncMeasurements()

        assertEquals(1, report.attempted)
        assertEquals(0, report.uploaded)
        assertEquals(0, report.markedUploaded)
        assertEquals(1, report.unexpectedErrors)
        assertEquals(0, local.markedMeasurementIds.size)
    }

    @Test
    fun syncMeasurements_countsNetworkErrors() = runBlocking {
        val now = Instant.fromEpochMilliseconds(1_710_000_000_000L)
        val m1 = Measurement(id = "m1", groupId = "g1", type = "download", timestamp = now)
        val local = FakeLocalStore(measurements = mutableListOf(m1))
        val remote = FakeRemoteDataSource(insertMeasurementMode = InsertMeasurementMode.NETWORK_ERROR)

        val report = MeasurementSyncUseCase(local, remote, FakeTcpTupleProvider()).syncMeasurements()

        assertEquals(1, report.attempted)
        assertEquals(1, report.networkErrors)
        assertEquals(0, local.markedMeasurementIds.size)
    }

    @Test
    fun syncFccSubmissions_patchesTupleAndMarksUploaded() = runBlocking {
        val submission = FccSubmission(id = "group-1", submitted = false)
        val local = FakeLocalStore(submissions = mutableListOf(submission))
        val remote = FakeRemoteDataSource(insertSubmissionMode = InsertSubmissionMode.SUCCESS)
        val tupleProvider = FakeTcpTupleProvider(
            result = Result.success(TcpTuple("203.0.113.1", 4242, 1_710_000_000_000L)),
        )

        val report = MeasurementSyncUseCase(local, remote, tupleProvider).syncFccSubmissions()

        assertEquals(1, report.attempted)
        assertEquals(1, report.uploaded)
        assertEquals(1, local.markedSubmissionIds.size)
        assertEquals("group-1", local.markedSubmissionIds.first())
        assertEquals("203.0.113.1", remote.lastSubmission?.sourceIp)
        assertEquals(4242, remote.lastSubmission?.sourcePort)
    }

    @Test
    fun syncFccSubmissions_stopsWhenTupleLookupFails() = runBlocking {
        val submission = FccSubmission(id = "group-1", submitted = false)
        val local = FakeLocalStore(submissions = mutableListOf(submission))
        val remote = FakeRemoteDataSource(insertSubmissionMode = InsertSubmissionMode.SUCCESS)
        val tupleProvider = FakeTcpTupleProvider(result = Result.failure(Exception("tuple-fail")))

        val report = MeasurementSyncUseCase(local, remote, tupleProvider).syncFccSubmissions()

        assertEquals(1, report.attempted)
        assertEquals(true, report.blockedBeforeUpload)
        assertEquals(1, report.unexpectedErrors)
        assertEquals(0, local.markedSubmissionIds.size)
    }
}

private enum class InsertMeasurementMode {
    SUCCESS,
    NETWORK_ERROR,
    DUPLICATE,
}

private enum class InsertSubmissionMode {
    SUCCESS,
    NETWORK_ERROR,
    UNEXPECTED,
}

private class FakeLocalStore(
    val measurements: MutableList<Measurement> = mutableListOf(),
    val submissions: MutableList<FccSubmission> = mutableListOf(),
) : MeasurementSyncLocalStore {
    val markedMeasurementIds: MutableList<String> = mutableListOf()
    val markedSubmissionIds: MutableList<String> = mutableListOf()

    override suspend fun getUnsyncedMeasurements(): List<Measurement> = measurements.toList()

    override suspend fun markMeasurementUploaded(id: String, uploadedAt: Instant) {
        markedMeasurementIds += id
    }

    override suspend fun getUnsyncedSubmissions(): List<FccSubmission> = submissions.toList()

    override suspend fun markSubmissionUploaded(id: String, uploadedAt: Instant) {
        markedSubmissionIds += id
    }
}

private class FakeRemoteDataSource(
    private val insertMeasurementMode: InsertMeasurementMode = InsertMeasurementMode.SUCCESS,
    private val insertSubmissionMode: InsertSubmissionMode = InsertSubmissionMode.SUCCESS,
    private val getMeasurementByIdResult: Result<Measurement> = Result.failure(NotFoundError()),
) : MeasurementSyncRemoteDataSource {
    var lastSubmission: FccSubmission? = null

    override suspend fun insertMeasurement(measurement: Measurement): Measurement = when (insertMeasurementMode) {
        InsertMeasurementMode.SUCCESS -> measurement
        InsertMeasurementMode.NETWORK_ERROR -> throw NetworkError()
        InsertMeasurementMode.DUPLICATE -> throw DuplicateKeyError()
    }

    override suspend fun getMeasurementById(id: String): Measurement =
        getMeasurementByIdResult.getOrElse { throw it }

    override suspend fun insertFccSubmission(submission: FccSubmission): FccSubmission {
        lastSubmission = submission
        return when (insertSubmissionMode) {
            InsertSubmissionMode.SUCCESS -> submission
            InsertSubmissionMode.NETWORK_ERROR -> throw NetworkError()
            InsertSubmissionMode.UNEXPECTED -> throw IllegalStateException("unexpected")
        }
    }
}

private class FakeTcpTupleProvider(
    private val result: Result<TcpTuple> = Result.success(TcpTuple("198.51.100.2", 9999, 1_710_000_000_000L)),
) : TcpTupleProvider {
    override suspend fun getPublicTcpTuple(): TcpTuple = result.getOrElse { throw it }
}
