package edu.gatech.cc.cellwatch.domain.fcc

import edu.gatech.cc.cellwatch.domain.capability.CapabilitySupport
import edu.gatech.cc.cellwatch.domain.capability.DeviceCapabilitySnapshot
import edu.gatech.cc.cellwatch.domain.capability.LocationCapabilitySnapshot
import edu.gatech.cc.cellwatch.domain.capability.NetworkCapabilitySnapshot
import edu.gatech.cc.cellwatch.domain.capability.PlatformCapabilityProvider
import edu.gatech.cc.cellwatch.domain.capability.PlatformCapabilitySnapshot
import edu.gatech.cc.cellwatch.domain.capability.TelephonyCapabilitySnapshot
import edu.gatech.cc.cellwatch.domain.model.NetworkConnectionType
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class MsakMeasurementExecutorPlatformCapabilityTest {

    @Test
    fun runLatency_enrichesMeasurementFromCapabilitySnapshot() = runBlocking {
        val executor = MsakMeasurementExecutorPlatform.create(
            config = MsakMeasurementExecutorConfig(
                userAgent = "jvm-test",
                capabilityProvider = FakeCapabilityProvider,
            ),
        )

        val measurement = executor.runLatency(
            server = MsakServerEndpoint(
                machine = "msak-test.example",
                urls = emptyMap(),
                latencyUdpPort = null,
            ),
            groupId = "group-1",
            measurementId = "latency-1",
        )

        assertEquals("Acme", measurement.deviceManufacturer)
        assertEquals("Model-A", measurement.deviceModel)
        assertEquals("JVM OS", measurement.deviceOsName)
        assertEquals("310", measurement.simMcc)
        assertEquals("260", measurement.simMnc)
        assertEquals("311", measurement.netMcc)
        assertEquals("480", measurement.netMnc)
        assertEquals("msak-test.example", measurement.provider)
        assertEquals(NetworkConnectionType.CELLULAR, measurement.connectionType)
    }
}

private object FakeCapabilityProvider : PlatformCapabilityProvider {
    override suspend fun captureSnapshot(): PlatformCapabilitySnapshot {
        return PlatformCapabilitySnapshot(
            telephony = TelephonyCapabilitySnapshot(
                support = CapabilitySupport.AVAILABLE,
                provider = "provider-from-capability",
                simMcc = "310",
                simMnc = "260",
                netMcc = "311",
                netMnc = "480",
            ),
            network = NetworkCapabilitySnapshot(
                support = CapabilitySupport.AVAILABLE,
                connected = true,
                available = true,
                roaming = false,
                connectionType = NetworkConnectionType.WIFI,
                cellularDataEnabled = false,
            ),
            location = LocationCapabilitySnapshot(
                support = CapabilitySupport.UNAVAILABLE,
            ),
            device = DeviceCapabilitySnapshot(
                support = CapabilitySupport.AVAILABLE,
                manufacturer = "Acme",
                model = "Model-A",
                osName = "JVM OS",
                osVersion = "1.0",
                appVersion = "test-app",
            ),
        )
    }
}
