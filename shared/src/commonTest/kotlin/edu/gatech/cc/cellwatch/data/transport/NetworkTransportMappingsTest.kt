package edu.gatech.cc.cellwatch.data.transport

import edu.gatech.cc.cellwatch.domain.model.Cell
import edu.gatech.cc.cellwatch.domain.model.FccSubmission
import edu.gatech.cc.cellwatch.domain.model.LatencyData
import edu.gatech.cc.cellwatch.domain.model.Location
import edu.gatech.cc.cellwatch.domain.model.Measurement
import edu.gatech.cc.cellwatch.domain.model.NetworkConnectionType
import edu.gatech.cc.cellwatch.domain.model.UploadDownloadData
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class NetworkTransportMappingsTest {

    @Test
    fun measurement_roundTrips_withNestedData() {
        val now = Instant.fromEpochMilliseconds(1_710_000_000_000L)
        val measurement = Measurement(
            id = "m1",
            groupId = "g1",
            type = "download",
            timestamp = now,
            connectionType = NetworkConnectionType.CELLULAR,
            cellularDataEnabled = true,
            uploadDownloadData = UploadDownloadData(id = "ud1", measurementId = "m1", bytes = 100),
            latencyData = LatencyData(id = "ld1", measurementId = "m1", rtt = 24),
            locations = listOf(Location(id = "loc1", lat = 33.7, lon = -84.3, measurementId = "m1")),
            cells = listOf(
                Cell(
                    id = "c1",
                    timestamp = null,
                    cellId = null,
                    physicalCellId = null,
                    cellConnection = null,
                    networkGeneration = null,
                    networkSubtype = null,
                    signalStrength = -98,
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
            ),
            appVersion = "1.2.3",
        )

        val roundTrip = measurement.toNetwork().toDomain()

        assertEquals(measurement.id, roundTrip.id)
        assertEquals(measurement.groupId, roundTrip.groupId)
        assertEquals(measurement.connectionType, roundTrip.connectionType)
        assertEquals(100, roundTrip.uploadDownloadData?.bytes)
        assertEquals(24, roundTrip.latencyData?.rtt)
        assertEquals(1, roundTrip.locations?.size)
        assertEquals(1, roundTrip.cells?.size)
        assertEquals("1.2.3", roundTrip.appVersion)
    }

    @Test
    fun measurementWithData_roundTrips() {
        val measurement = Measurement(
            id = "m2",
            type = "latency",
            latencyData = LatencyData(id = "ld2", measurementId = "m2", rtt = 31),
        )

        val roundTrip = measurement.toNetworkWithData().toDomain()

        assertEquals("m2", roundTrip.id)
        assertEquals("latency", roundTrip.type)
        assertEquals(31, roundTrip.latencyData?.rtt)
    }

    @Test
    fun fccSubmission_roundTrips() {
        val now = Instant.fromEpochMilliseconds(1_710_000_000_000L)
        val submission = FccSubmission(
            id = "s1",
            contactName = "Alice",
            sourceIp = "203.0.113.1",
            sourcePort = 443,
            submitted = false,
            createdOn = now,
        )

        val roundTrip = submission.toNetwork().toDomain()

        assertEquals("s1", roundTrip.id)
        assertEquals("Alice", roundTrip.contactName)
        assertEquals("203.0.113.1", roundTrip.sourceIp)
        assertEquals(443, roundTrip.sourcePort)
        assertEquals(false, roundTrip.submitted)
    }

    @Test
    fun networkLocation_withoutCoordinates_mapsToNull() {
        val location = NetworkLocation(id = "n1", lat = null, lon = -84.3)
        assertEquals(null, location.toDomainOrNull())
    }

    @Test
    fun networkLocation_withCoordinates_mapsToDomain() {
        val location = NetworkLocation(id = "n2", lat = 33.7, lon = -84.3)
        val domain = location.toDomainOrNull()
        assertNotNull(domain)
        assertEquals(33.7, domain.lat)
        assertEquals(-84.3, domain.lon)
    }
}
