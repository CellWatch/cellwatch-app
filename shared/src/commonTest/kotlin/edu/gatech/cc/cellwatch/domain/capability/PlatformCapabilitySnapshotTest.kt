package edu.gatech.cc.cellwatch.domain.capability

import edu.gatech.cc.cellwatch.domain.model.Measurement
import edu.gatech.cc.cellwatch.domain.model.NetworkConnectionType
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PlatformCapabilitySnapshotTest {

    @Test
    fun noOpProvider_returnsBestEffortNotSupportedSignals() {
        runBlocking {
            val snapshot = NoOpPlatformCapabilityProvider.captureSnapshot()

            assertEquals(CapabilitySupport.NOT_SUPPORTED, snapshot.telephony.support)
            assertEquals(CapabilitySupport.NOT_SUPPORTED, snapshot.location.support)
            assertNotNull(snapshot.telephony.note)
            assertNotNull(snapshot.location.note)
        }
    }

    @Test
    fun enricher_fillsMissingValuesWithoutClobberingExistingValues() {
        val snapshot = PlatformCapabilitySnapshot(
            telephony = TelephonyCapabilitySnapshot(
                support = CapabilitySupport.AVAILABLE,
                provider = "test-carrier",
                simMcc = "310",
                simMnc = "410",
                netMcc = "310",
                netMnc = "260",
                note = "telephony good",
            ),
            network = NetworkCapabilitySnapshot(
                support = CapabilitySupport.AVAILABLE,
                connected = true,
                available = true,
                roaming = false,
                connectionType = NetworkConnectionType.CELLULAR,
                cellularDataEnabled = true,
                note = "network sampled",
            ),
            location = LocationCapabilitySnapshot(
                support = CapabilitySupport.PARTIAL,
                note = "location coarse only",
            ),
            device = DeviceCapabilitySnapshot(
                support = CapabilitySupport.AVAILABLE,
                manufacturer = "Acme",
                model = "X1",
                osName = "iOS",
                osVersion = "18.0",
                appVersion = "1.2.3",
                note = "device metadata captured",
            ),
        )
        val base = Measurement(
            type = "latency",
            provider = "already-set",
            connectionType = NetworkConnectionType.WIFI,
        )

        val enriched = MeasurementCapabilityEnricher().enrich(base, snapshot)

        assertEquals("already-set", enriched.provider)
        assertEquals(NetworkConnectionType.WIFI, enriched.connectionType)
        assertEquals("310", enriched.simMcc)
        assertEquals("410", enriched.simMnc)
        assertEquals("Acme", enriched.deviceManufacturer)
        assertEquals("X1", enriched.deviceModel)
        assertEquals(true, enriched.networkConnected)
        assertEquals(false, enriched.networkRoaming)
        assertEquals(true, enriched.cellularDataEnabled)
        assertEquals("AVAILABLE", enriched.telephonySupport)
        assertEquals("AVAILABLE", enriched.networkSupport)
        assertEquals("PARTIAL", enriched.locationSupport)
        assertEquals("AVAILABLE", enriched.deviceSupport)
        assertTrue(enriched.capabilityNotes?.contains("telephony:telephony good") == true)
        assertTrue(enriched.capabilityNotes?.contains("location:location coarse only") == true)
    }

    @Test
    fun phase4BestEffortContract_allowsPartialAndMissingData() {
        val snapshot = PlatformCapabilitySnapshot(
            telephony = TelephonyCapabilitySnapshot(
                support = CapabilitySupport.PERMISSION_DENIED,
                note = "phone state permission denied",
            ),
            network = NetworkCapabilitySnapshot(
                support = CapabilitySupport.PARTIAL,
                connected = true,
                available = true,
                connectionType = null,
            ),
            location = LocationCapabilitySnapshot(
                support = CapabilitySupport.PERMISSION_DENIED,
            ),
            device = DeviceCapabilitySnapshot(
                support = CapabilitySupport.PARTIAL,
                osName = "iOS",
            ),
        )

        assertEquals(CapabilitySupport.PERMISSION_DENIED, snapshot.telephony.support)
        assertEquals(CapabilitySupport.PARTIAL, snapshot.network.support)
        assertTrue(snapshot.network.connected == true)
        assertEquals(null, snapshot.network.connectionType)
        assertEquals("iOS", snapshot.device.osName)
    }
}
