package edu.gatech.cc.cellwatch.domain.export

import edu.gatech.cc.cellwatch.domain.model.FccSubmission
import edu.gatech.cc.cellwatch.domain.model.LatencyData
import edu.gatech.cc.cellwatch.domain.model.Location
import edu.gatech.cc.cellwatch.domain.model.Measurement
import edu.gatech.cc.cellwatch.domain.model.MeasurementGroup
import edu.gatech.cc.cellwatch.domain.model.NetworkConnectionType
import edu.gatech.cc.cellwatch.domain.model.UploadDownloadData
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The wire format the FCC accepts, ported from frozenApp. A change here
 * changes what gets filed, so the shape is asserted rather than assumed.
 */
class FccSubmissionExportTest {

    private val now = Instant.parse("2026-09-20T12:00:00Z")

    private fun measurement(type: String) = Measurement(
        id = "m-$type",
        groupId = "g1",
        type = type,
        timestamp = now,
        duration = 5_000_000,
        success = true,
        connectionType = NetworkConnectionType.CELLULAR,
        provider = "Test Carrier",
        deviceManufacturer = "Acme",
        deviceModel = "Handset",
        deviceOsName = "Android",
        deviceOsVersion = "16",
        locations = listOf(
            Location(id = "loc1", timestamp = now, lat = 33.775612345, lon = -84.396387654),
        ),
        latencyData = if (type == "latency") {
            LatencyData(id = "l1", measurementId = "m-$type", rtt = 22_000, jitter = 2_000, sent = 30, received = 30)
        } else {
            null
        },
        uploadDownloadData = if (type != "latency") {
            UploadDownloadData(
                id = "u-$type",
                measurementId = "m-$type",
                bytes = 8_000_000,
                bytesPerSec = 1_600_000.4,
                servers = listOf("mlab-test"),
            )
        } else {
            null
        },
    )

    private fun group(submission: FccSubmission?) = MeasurementGroup(
        latency = measurement("latency"),
        download = measurement("download"),
        upload = measurement("upload"),
        submission = submission,
        id = "g1",
    )

    @Test
    fun `a group with no submission produces no export`() {
        assertNull(group(submission = null).toFccSubmissionExport())
    }

    @Test
    fun `a submitted group maps to the FCC shape`() {
        val export = group(
            submission = FccSubmission(id = "g1", inVehicle = false, deviceType = "Phone"),
        ).toFccSubmissionExport()

        assertNotNull(export)
        assertEquals("Phone", export.device_type)
        assertEquals("Android 16", export.operating_system)
        assertEquals("Test Carrier", export.provider_name)
        // 0 outdoor stationary, 1 in-vehicle mobile.
        assertEquals(0, export.environment_code)
        assertNotNull(export.tests.latency)
        assertNotNull(export.tests.download)
        assertEquals(22_000, export.tests.latency?.round_trip_time)
        assertEquals(8_000_000, export.tests.download?.bytes_transferred)
        assertEquals(1_600_000, export.tests.download?.bytes_sec)
        assertEquals("cell", export.tests.download?.connection_type)
    }

    @Test
    fun `in-vehicle runs report the mobile environment code`() {
        val export = group(submission = FccSubmission(id = "g1", inVehicle = true)).toFccSubmissionExport()
        assertEquals(1, export?.environment_code)
    }

    @Test
    fun `coordinates keep six decimal places without String format`() {
        // String.format is JVM-only; this is the multiplatform replacement, and
        // truncating coordinates would move a measurement on the map.
        val location = group(submission = FccSubmission(id = "g1"))
            .toFccSubmissionExport()?.tests?.latency?.locations?.first()
        assertEquals("33.775612", location?.latitude)
        assertEquals("-84.396388", location?.longitude)
    }
}
