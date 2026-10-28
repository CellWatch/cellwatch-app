package edu.gatech.cc.cellwatch.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FccExportExtensionsTest {
    private val groupId = "3f2c9a8e-1b4d-4c6e-9f00-a1b2c3d4e5f6"

    // The released app exports test_id as the latency measurement's id. Changing it to the
    // submission id changes FCC data, so that change waits for FCC review (branch
    // measurement-process-changes).
    @Test
    fun testIdIsTheLatencyMeasurementIdAsReleased() {
        val group = group(groupId, FccSubmission(id = groupId))

        assertEquals("latency-measurement", group.toFccSubmissionExport()?.test_id)
    }

    @Test
    fun groupWithoutASubmissionIsNotExported() {
        assertNull(group(groupId, submission = null).toFccSubmissionExport())
    }

    private fun group(id: String, submission: FccSubmission?) = MeasurementGroup(
        latency = Measurement(
            id = "latency-measurement",
            groupId = id,
            type = "latency",
            latencyData = LatencyData(),
            connectionType = null,
            cellularDataEnabled = null,
        ),
        download = Measurement(
            id = "download-measurement",
            groupId = id,
            type = "download",
            uploadDownloadData = UploadDownloadData(),
            connectionType = null,
            cellularDataEnabled = null,
        ),
        upload = Measurement(
            id = "upload-measurement",
            groupId = id,
            type = "upload",
            uploadDownloadData = UploadDownloadData(),
            connectionType = null,
            cellularDataEnabled = null,
        ),
        submission = submission,
    )
}
