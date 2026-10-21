package edu.gatech.cc.cellwatch.data.model

import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MeasurementGroupUploadTimeTest {
    private val t1 = Instant.fromEpochMilliseconds(1_790_000_000_000)
    private val t2 = Instant.fromEpochMilliseconds(1_790_000_060_000)
    private val t3 = Instant.fromEpochMilliseconds(1_790_000_120_000)
    private val t4 = Instant.fromEpochMilliseconds(1_790_000_180_000)

    @Test
    fun latestTimeOnceEveryMeasurementAndTheSubmissionAreUploaded() {
        val group = group(latency = t1, download = t3, upload = t2, submission = t4)

        assertEquals(t4, group.storedUploadTime())
    }

    @Test
    fun latestTimeIsUsedWhicheverRecordUploadedLast() {
        val group = group(latency = t1, download = t4, upload = t2, submission = t3)

        assertEquals(t4, group.storedUploadTime())
    }

    @Test
    fun notUploadedWhileTheDownloadOrUploadIsStillPending() {
        // The latency measurement and the submission alone used to be enough.
        assertNull(group(latency = t1, download = null, upload = t2, submission = t3).storedUploadTime())
        assertNull(group(latency = t1, download = t2, upload = null, submission = t3).storedUploadTime())
    }

    @Test
    fun notUploadedWhileTheSubmissionIsStillPending() {
        assertNull(group(latency = t1, download = t2, upload = t3, submission = null, hasSubmission = true).storedUploadTime())
    }

    @Test
    fun groupWithoutASubmissionIsUploadedOnceItsMeasurementsAre() {
        val group = group(latency = t1, download = t3, upload = t2, submission = null, hasSubmission = false)

        assertEquals(t3, group.storedUploadTime())
    }

    @Test
    fun onlyTheMeasurementsTheGroupHasAreRequired() {
        val group = MeasurementGroup(
            latency = measurement("g1", "latency", t2),
            download = null,
            upload = null,
            submission = null,
        )

        assertEquals(t2, group.storedUploadTime())
    }

    @Test
    fun emptyGroupIsNotUploaded() {
        assertNull(MeasurementGroup(null, null, null, null, id = "g1").storedUploadTime())
    }

    @Test
    fun timesComeFromTheLookupsNotTheGroupSnapshot() {
        // The repository reads current upload times from the database for a group captured before
        // the upload, whose own records still have no upload time.
        val stale = group(latency = null, download = null, upload = null, submission = null, hasSubmission = true)
        val stored = mapOf("g1-latency" to t1, "g1-download" to t2, "g1-upload" to t3)

        assertEquals(t4, stale.uploadTime({ stored[it.id] }, { t4 }))
        assertNull(stale.uploadTime({ stored[it.id] }, { null }))
    }

    private fun MeasurementGroup.storedUploadTime() = uploadTime({ it.uploadTime }, { it.uploadTime })

    private fun group(
        latency: Instant?,
        download: Instant?,
        upload: Instant?,
        submission: Instant?,
        hasSubmission: Boolean = true,
    ) = MeasurementGroup(
        latency = measurement("g1", "latency", latency),
        download = measurement("g1", "download", download),
        upload = measurement("g1", "upload", upload),
        submission = if (hasSubmission) FccSubmission(id = "g1", uploadTime = submission) else null,
    )

    private fun measurement(groupId: String, type: String, uploadTime: Instant?) = Measurement(
        id = "$groupId-$type",
        groupId = groupId,
        type = type,
        latencyData = if (type == "latency") LatencyData() else null,
        uploadDownloadData = if (type != "latency") UploadDownloadData() else null,
        connectionType = null,
        cellularDataEnabled = null,
        uploadTime = uploadTime,
    )
}
