package edu.gatech.cc.cellwatch.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MeasurementGroupTest {

    @Test
    fun id_is_inferred_from_group_members() {
        val latency = Measurement(
            id = "m-l",
            groupId = "g-1",
            type = "latency",
            latencyData = LatencyData(),
        )
        val group = MeasurementGroup(
            latency = latency,
            download = null,
            upload = null,
            submission = null,
        )

        assertEquals("g-1", group.id)
    }

    @Test
    fun rejects_latency_with_wrong_type() {
        assertFailsWith<IllegalStateException> {
            MeasurementGroup(
                latency = Measurement(
                    groupId = "g-1",
                    type = "download",
                    latencyData = LatencyData(),
                ),
                download = null,
                upload = null,
                submission = null,
                id = "g-1",
            )
        }
    }

    @Test
    fun rejects_download_without_payload() {
        assertFailsWith<IllegalStateException> {
            MeasurementGroup(
                latency = null,
                download = Measurement(
                    groupId = "g-1",
                    type = "download",
                    uploadDownloadData = null,
                ),
                upload = null,
                submission = null,
                id = "g-1",
            )
        }
    }

    @Test
    fun centerLatLon_averages_measurement_centers() {
        val latency = Measurement(
            groupId = "g-1",
            type = "latency",
            latencyData = LatencyData(),
            locations = listOf(Location(lat = 10.0, lon = 10.0)),
        )
        val upload = Measurement(
            groupId = "g-1",
            type = "upload",
            uploadDownloadData = UploadDownloadData(),
            locations = listOf(Location(lat = 20.0, lon = 30.0)),
        )

        val group = MeasurementGroup(
            latency = latency,
            download = null,
            upload = upload,
            submission = null,
        )

        assertEquals(Pair(15.0, 20.0), group.centerLatLon())
    }
}
