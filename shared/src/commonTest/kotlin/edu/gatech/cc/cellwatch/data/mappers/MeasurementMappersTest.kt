package edu.gatech.cc.cellwatch.data.mappers

import edu.gatech.cc.cellwatch.domain.model.Measurement
import edu.gatech.cc.cellwatch.domain.model.NetworkConnectionType
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class MeasurementMappersTest {

    @Test
    fun round_trip_preserves_boolean_and_enum_fields() {
        val now = Instant.fromEpochMilliseconds(1_710_000_000_000L)
        val measurement = Measurement(
            id = "m-1",
            groupId = "g-1",
            type = "download",
            timestamp = now,
            scheduled = true,
            success = false,
            carrierAggregation = true,
            networkConnected = true,
            networkAvailable = true,
            networkRoaming = false,
            telephonySupport = "AVAILABLE",
            networkSupport = "PARTIAL",
            locationSupport = "PERMISSION_DENIED",
            deviceSupport = "AVAILABLE",
            capabilityNotes = "telephony:ok | location:denied",
            connectionType = NetworkConnectionType.CELLULAR,
            cellularDataEnabled = true,
            createdOn = now,
            updatedOn = now,
            appVersion = "1.2.3",
        )

        val back = measurement.toRow().toDomain()

        assertEquals(measurement.id, back.id)
        assertEquals(measurement.groupId, back.groupId)
        assertEquals(measurement.type, back.type)
        assertEquals(measurement.scheduled, back.scheduled)
        assertEquals(measurement.success, back.success)
        assertEquals(measurement.connectionType, back.connectionType)
        assertEquals(measurement.cellularDataEnabled, back.cellularDataEnabled)
        assertEquals(measurement.telephonySupport, back.telephonySupport)
        assertEquals(measurement.locationSupport, back.locationSupport)
        assertEquals(measurement.capabilityNotes, back.capabilityNotes)
    }
}
