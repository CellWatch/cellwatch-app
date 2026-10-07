package edu.gatech.cc.cellwatch.data.core.sync

import edu.gatech.cc.cellwatch.data.model.FccSubmission
import edu.gatech.cc.cellwatch.data.model.Measurement
import edu.gatech.cc.cellwatch.data.model.TcpTuple
import edu.gatech.cc.cellwatch.data.network.NetworkMeasurementDatasource.DuplicateKeyError
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.io.IOException

/**
 * Uploads locally stored measurements, then FCC submissions, to Supabase.
 *
 * Only one upload runs at a time, and at most [maxParallelUploads] requests are in flight at once.
 * A submission is only sent after all three of its measurements are uploaded, so the server never
 * receives a submission it can't complete.
 */
class MeasurementUploader(
    private val local: Local,
    private val remote: Remote,
    private val log: Log,
    private val maxParallelUploads: Int = 3,
    private val now: () -> Instant = { Clock.System.now() },
) {
    interface Local {
        suspend fun unsyncedMeasurements(): List<Measurement>
        suspend fun markMeasurementUploaded(measurement: Measurement, uploadTime: Instant)
        suspend fun unsyncedSubmissions(): List<FccSubmission>
        suspend fun measurementsInGroup(groupId: String): List<Measurement>
        suspend fun markSubmissionUploaded(submission: FccSubmission, uploadTime: Instant)
    }

    interface Remote {
        suspend fun insertMeasurement(measurement: Measurement): Measurement
        suspend fun insertFccSubmission(submission: FccSubmission): FccSubmission
        suspend fun getTcpTuple(): TcpTuple
    }

    interface Log {
        fun info(message: String, throwable: Throwable? = null)
        fun error(message: String, throwable: Throwable? = null)
    }

    /** What one run did. [waiting] counts submissions held back until their measurements upload. */
    data class Summary(val uploaded: Int = 0, val failed: Int = 0, val waiting: Int = 0)

    private val mutex = Mutex()

    suspend fun uploadPending(): Summary = mutex.withLock {
        val measurements = uploadMeasurements()
        val submissions = uploadSubmissions()
        Summary(
            uploaded = measurements.uploaded + submissions.uploaded,
            failed = measurements.failed + submissions.failed,
            waiting = submissions.waiting,
        )
    }

    private suspend fun uploadMeasurements(): Summary =
        uploadEach(local.unsyncedMeasurements(), { "measurement ${it.id}" }) { measurement ->
            val onServer = try {
                remote.insertMeasurement(measurement)
            } catch (e: DuplicateKeyError) {
                // insert_measurement saves a measurement and all of its data in one transaction, so
                // the server only has this id if an earlier attempt went through, even if this phone
                // never got the response.
                log.info("measurement ${measurement.id} was already on the server")
                measurement
            }
            local.markMeasurementUploaded(onServer, now())
        }

    private suspend fun uploadSubmissions(): Summary {
        val (ready, waiting) = local.unsyncedSubmissions().partition { measurementsUploaded(it) }
        if (waiting.isNotEmpty()) {
            log.info("${waiting.size} submissions are waiting for their measurements to upload")
        }
        if (ready.isEmpty()) {
            return Summary(waiting = waiting.size)
        }

        val tuple = try {
            remote.getTcpTuple()
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            log.info("network error getting tcp tuple", e)
            return Summary(failed = ready.size, waiting = waiting.size)
        } catch (e: Exception) {
            log.error("unexpected error getting tcp tuple", e)
            return Summary(failed = ready.size, waiting = waiting.size)
        }

        ready.forEach {
            it.sourceIp = tuple.remoteAddress
            it.sourcePort = tuple.remotePort
            it.serverTimestamp = Instant.fromEpochMilliseconds(tuple.timestamp)
        }

        return uploadEach(ready, { "submission ${it.id}" }) { submission ->
            val onServer = try {
                remote.insertFccSubmission(submission)
            } catch (e: DuplicateKeyError) {
                log.info("submission ${submission.id} was already on the server")
                submission
            }
            local.markSubmissionUploaded(onServer, now())
        }.copy(waiting = waiting.size)
    }

    /** A submission's id is its group id, and the server needs all three of the group's tests. */
    private suspend fun measurementsUploaded(submission: FccSubmission): Boolean {
        val group = local.measurementsInGroup(submission.id)
        return TEST_TYPES.all { type -> group.any { it.type == type && it.uploadTime != null } }
    }

    private suspend fun <T> uploadEach(
        items: List<T>,
        describe: (T) -> String,
        upload: suspend (T) -> Unit,
    ): Summary {
        if (items.isEmpty()) {
            return Summary()
        }

        val permits = Semaphore(maxParallelUploads)
        val succeeded = coroutineScope {
            items.map { item ->
                async { permits.withPermit { tryUpload(describe(item)) { upload(item) } } }
            }.awaitAll()
        }

        return Summary(uploaded = succeeded.count { it }, failed = succeeded.count { !it })
    }

    private suspend fun tryUpload(what: String, upload: suspend () -> Unit): Boolean =
        try {
            upload()
            log.info("$what uploaded")
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            log.info("network error uploading $what", e)
            false
        } catch (e: Exception) {
            log.error("unexpected error uploading $what", e)
            false
        }

    private companion object {
        val TEST_TYPES = listOf("latency", "download", "upload")
    }
}
