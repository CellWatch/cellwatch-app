package edu.gatech.cc.cellwatch.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MeasurementTest {

    @Test
    fun centerLatLon_returnsNull_withoutLocations() {
        val measurement = Measurement(type = "latency")
        assertNull(measurement.centerLatLon())
    }

    @Test
    fun centerLatLon_returnsFirstLocation_whenOnlyOnePoint() {
        val measurement = Measurement(
            type = "download",
            locations = listOf(Location(lat = 10.0, lon = 20.0)),
        )

        assertEquals(Pair(10.0, 20.0), measurement.centerLatLon())
    }

    @Test
    fun centerLatLon_returnsMidpoint_ofFirstTwoLocations() {
        val measurement = Measurement(
            type = "upload",
            locations = listOf(
                Location(lat = 10.0, lon = 20.0),
                Location(lat = 14.0, lon = 30.0),
                Location(lat = 50.0, lon = 60.0),
            ),
        )

        assertEquals(Pair(12.0, 25.0), measurement.centerLatLon())
    }
}
