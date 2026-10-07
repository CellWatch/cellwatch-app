package edu.gatech.cc.cellwatch.data.core.sync

import edu.gatech.cc.cellwatch.data.model.FccSubmission
import edu.gatech.cc.cellwatch.data.model.Measurement
import edu.gatech.cc.cellwatch.data.model.TcpTuple
import edu.gatech.cc.cellwatch.data.network.NetworkMeasurementDatasource.DuplicateKeyError
import edu.gatech.cc.cellwatch.data.network.NetworkMeasurementDatasource.NetworkError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MeasurementUploaderTest {
    private val uploadTime = Instant.fromEpochMilliseconds(1_790_000_000_000)

    @Test
    fun measurementTheServerAlreadyHasIsMarkedUploadedWithoutAnotherRequest() = runBlocking<Unit> {
        val m = measurement("g1", "latency")
        val local = FakeLocal(listOf(m))
        val remote = FakeRemote(alreadyOnServer = setOf(m.id))

        val summary = uploader(local, remote).uploadPending()

        assertEquals(MeasurementUploader.Summary(uploaded = 1), summary)
        assertEquals(uploadTime, local.measurement(m.id).uploadTime)
        assertEquals(listOf(m.id), remote.requests)
    }

    @Test
    fun measurementSavedBeforeThePhoneTimedOutIsMarkedUploadedOnTheNextRun() = runBlocking<Unit> {
        val m = measurement("g1", "download")
        val local = FakeLocal(listOf(m))
        val remote = FakeRemote(savedThenTimedOut = setOf(m.id))
        val uploader = uploader(local, remote)

        assertEquals(1, uploader.uploadPending().failed)
        assertNull(local.measurement(m.id).uploadTime)

        assertEquals(1, uploader.uploadPending().uploaded)
        assertEquals(uploadTime, local.measurement(m.id).uploadTime)

        uploader.uploadPending()
        assertEquals("not sent again once marked", listOf(m.id, m.id), remote.requests)
    }

    @Test
    fun sendsAtMostThreeRequestsAtATime() = runBlocking<Unit> {
        val local = FakeLocal((1..4).flatMap { group("g$it") })
        val remote = FakeRemote()

        val summary = uploader(local, remote).uploadPending()

        assertEquals(12, summary.uploaded)
        assertEquals(3, remote.maxInFlight)
    }

    @Test
    fun overlappingRunsDoNotSendTheSameMeasurementTwice() = runBlocking<Unit> {
        val local = FakeLocal(group("g1") + group("g2"))
        val remote = FakeRemote()
        val uploader = uploader(local, remote)

        listOf(async { uploader.uploadPending() }, async { uploader.uploadPending() }).awaitAll()

        assertEquals(6, remote.requests.size)
        assertEquals(remote.requests.size, remote.requests.toSet().size)
        assertTrue(remote.maxInFlight <= 3)
    }

    @Test
    fun networkErrorLeavesOnlyThatMeasurementPending() = runBlocking<Unit> {
        val measurements = group("g1")
        val unreachable = measurements[1]
        val local = FakeLocal(measurements)
        val remote = FakeRemote(unreachable = setOf(unreachable.id))

        val summary = uploader(local, remote).uploadPending()

        assertEquals(MeasurementUploader.Summary(uploaded = 2, failed = 1), summary)
        assertNull(local.measurement(unreachable.id).uploadTime)
        measurements.filter { it.id != unreachable.id }.forEach { assertNotNull(local.measurement(it.id).uploadTime) }
    }

    @Test
    fun submissionIsSentOnlyAfterAllThreeOfItsMeasurementsAreUploaded() = runBlocking<Unit> {
        val complete = group("g1")
        val incomplete = group("g2")
        val local = FakeLocal(complete + incomplete, listOf(FccSubmission(id = "g1"), FccSubmission(id = "g2")))
        val remote = FakeRemote(unreachable = setOf(incomplete.last().id))

        val summary = uploader(local, remote).uploadPending()

        assertEquals(uploadTime, local.submission("g1").uploadTime)
        assertNull(local.submission("g2").uploadTime)
        assertFalse("g2" in remote.requests)
        assertEquals(1, summary.waiting)

        val sent = remote.sentSubmissions.single()
        assertEquals(remote.tuple.remoteAddress, sent.sourceIp)
        assertEquals(remote.tuple.remotePort, sent.sourcePort)
        assertEquals(Instant.fromEpochMilliseconds(remote.tuple.timestamp), sent.serverTimestamp)
    }

    @Test
    fun submissionWaitingForMeasurementsIsSentOnceTheyUpload() = runBlocking<Unit> {
        val measurements = group("g1")
        val local = FakeLocal(measurements, listOf(FccSubmission(id = "g1")))
        val unreachable = mutableSetOf(measurements.last().id)
        val remote = FakeRemote(unreachable = unreachable)
        val uploader = uploader(local, remote)

        uploader.uploadPending()
        assertNull(local.submission("g1").uploadTime)

        unreachable.clear()
        uploader.uploadPending()
        assertEquals(uploadTime, local.submission("g1").uploadTime)
    }

    @Test
    fun submissionMissingOneOfItsMeasurementsIsNotSent() = runBlocking<Unit> {
        val local = FakeLocal(group("g1", listOf("latency", "download")), listOf(FccSubmission(id = "g1")))
        val remote = FakeRemote()

        val summary = uploader(local, remote).uploadPending()

        assertNull(local.submission("g1").uploadTime)
        assertEquals(1, summary.waiting)
        assertEquals("no tuple request when nothing is ready", 0, remote.tupleRequests)
    }

    @Test
    fun submissionTheServerAlreadyHasIsMarkedUploaded() = runBlocking<Unit> {
        val uploaded = group("g1").map { it.copy(uploadTime = uploadTime) }
        val local = FakeLocal(uploaded, listOf(FccSubmission(id = "g1")))
        val remote = FakeRemote(alreadyOnServer = setOf("g1"))

        val summary = uploader(local, remote).uploadPending()

        assertEquals(MeasurementUploader.Summary(uploaded = 1), summary)
        assertEquals(uploadTime, local.submission("g1").uploadTime)
    }

    @Test
    fun tupleFailureLeavesSubmissionsPending() = runBlocking<Unit> {
        val uploaded = group("g1").map { it.copy(uploadTime = uploadTime) }
        val local = FakeLocal(uploaded, listOf(FccSubmission(id = "g1")))
        val remote = FakeRemote(tupleUnreachable = true)

        val summary = uploader(local, remote).uploadPending()

        assertEquals(MeasurementUploader.Summary(failed = 1), summary)
        assertNull(local.submission("g1").uploadTime)
    }

    @Test
    fun cancellationStopsTheRunWithoutBeingReportedAsAnError() {
        val measurements = group("g1")
        val local = FakeLocal(measurements)
        val remote = FakeRemote(cancelOn = setOf(measurements.first().id))
        val log = FakeLog()

        assertThrows(CancellationException::class.java) {
            runBlocking { uploader(local, remote, log).uploadPending() }
        }
        assertEquals(emptyList<String>(), log.errors)
    }

    private fun uploader(local: FakeLocal, remote: FakeRemote, log: FakeLog = FakeLog()) =
        MeasurementUploader(local, remote, log, maxParallelUploads = 3, now = { uploadTime })

    private fun measurement(groupId: String, type: String) = Measurement(
        id = "$groupId-$type",
        groupId = groupId,
        type = type,
        connectionType = null,
        cellularDataEnabled = null,
    )

    private fun group(groupId: String, types: List<String> = listOf("latency", "download", "upload")) =
        types.map { measurement(groupId, it) }
}

private class FakeLocal(
    initialMeasurements: List<Measurement>,
    initialSubmissions: List<FccSubmission> = emptyList(),
) : MeasurementUploader.Local {
    private val measurements = initialMeasurements.associateBy { it.id }.toMutableMap()
    private val submissions = initialSubmissions.associateBy { it.id }.toMutableMap()

    fun measurement(id: String) = measurements.getValue(id)
    fun submission(id: String) = submissions.getValue(id)

    override suspend fun unsyncedMeasurements() = measurements.values.filter { it.uploadTime == null }

    override suspend fun markMeasurementUploaded(measurement: Measurement, uploadTime: Instant) {
        measurements[measurement.id] = measurement.copy(uploadTime = uploadTime)
    }

    override suspend fun unsyncedSubmissions() = submissions.values.filter { it.uploadTime == null }

    override suspend fun measurementsInGroup(groupId: String) = measurements.values.filter { it.groupId == groupId }

    override suspend fun markSubmissionUploaded(submission: FccSubmission, uploadTime: Instant) {
        submissions[submission.id] = submission.copy(uploadTime = uploadTime)
    }
}

/** A server that takes a little while to answer, so overlapping requests can be counted. */
private class FakeRemote(
    alreadyOnServer: Set<String> = emptySet(),
    private val unreachable: Set<String> = emptySet(),
    private val savedThenTimedOut: Set<String> = emptySet(),
    private val cancelOn: Set<String> = emptySet(),
    private val tupleUnreachable: Boolean = false,
) : MeasurementUploader.Remote {
    val tuple = TcpTuple("203.0.113.7", 40000, 1_790_000_000_000)
    val requests = mutableListOf<String>()
    val sentSubmissions = mutableListOf<FccSubmission>()
    var tupleRequests = 0
    var maxInFlight = 0
    private var inFlight = 0
    private val onServer = alreadyOnServer.toMutableSet()

    override suspend fun insertMeasurement(measurement: Measurement) = insert(measurement.id) { measurement }

    override suspend fun insertFccSubmission(submission: FccSubmission) =
        insert(submission.id) { submission.also { sentSubmissions += it } }

    override suspend fun getTcpTuple(): TcpTuple {
        tupleRequests++
        if (tupleUnreachable) throw NetworkError()
        return tuple
    }

    private suspend fun <T> insert(id: String, saved: () -> T): T {
        requests += id
        inFlight++
        maxInFlight = maxOf(maxInFlight, inFlight)
        try {
            delay(10)
            if (id in cancelOn) throw CancellationException("cancelled")
            if (id in unreachable) throw NetworkError()
            if (id in onServer) throw DuplicateKeyError()
            onServer += id
            if (id in savedThenTimedOut) throw NetworkError()
            return saved()
        } finally {
            inFlight--
        }
    }
}

private class FakeLog : MeasurementUploader.Log {
    val errors = mutableListOf<String>()

    override fun info(message: String, throwable: Throwable?) {}

    override fun error(message: String, throwable: Throwable?) {
        errors += message
    }
}
