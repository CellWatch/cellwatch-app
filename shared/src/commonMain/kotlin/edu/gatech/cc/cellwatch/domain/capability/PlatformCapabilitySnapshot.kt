package edu.gatech.cc.cellwatch.domain.capability

import edu.gatech.cc.cellwatch.domain.model.Cell
import edu.gatech.cc.cellwatch.domain.model.Location
import edu.gatech.cc.cellwatch.domain.model.Measurement
import edu.gatech.cc.cellwatch.domain.model.NetworkConnectionType
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

enum class CapabilitySupport {
    AVAILABLE,
    PARTIAL,
    UNAVAILABLE,
    PERMISSION_DENIED,
    NOT_SUPPORTED,
}

data class TelephonyCapabilitySnapshot(
    val support: CapabilitySupport,
    val provider: String? = null,
    val simMcc: String? = null,
    val simMnc: String? = null,
    val netMcc: String? = null,
    val netMnc: String? = null,
    val networkGeneration: String? = null,
    val networkSubtype: String? = null,
    val cells: List<Cell> = emptyList(),
    val note: String? = null,
)

data class NetworkCapabilitySnapshot(
    val support: CapabilitySupport,
    val connected: Boolean? = null,
    val available: Boolean? = null,
    val roaming: Boolean? = null,
    val connectionType: NetworkConnectionType? = null,
    val cellularDataEnabled: Boolean? = null,
    val note: String? = null,
)

data class LocationCapabilitySnapshot(
    val support: CapabilitySupport,
    val samples: List<Location> = emptyList(),
    val note: String? = null,
)

data class DeviceCapabilitySnapshot(
    val support: CapabilitySupport,
    val manufacturer: String? = null,
    val model: String? = null,
    val osName: String? = null,
    val osVersion: String? = null,
    val appVersion: String? = null,
    val note: String? = null,
)

data class PlatformCapabilitySnapshot(
    val capturedAt: Instant = Clock.System.now(),
    val telephony: TelephonyCapabilitySnapshot,
    val network: NetworkCapabilitySnapshot,
    val location: LocationCapabilitySnapshot,
    val device: DeviceCapabilitySnapshot,
)

interface PlatformCapabilityProvider {
    suspend fun captureSnapshot(): PlatformCapabilitySnapshot
}

object NoOpPlatformCapabilityProvider : PlatformCapabilityProvider {
    override suspend fun captureSnapshot(): PlatformCapabilitySnapshot {
        return PlatformCapabilitySnapshot(
            telephony = TelephonyCapabilitySnapshot(
                support = CapabilitySupport.NOT_SUPPORTED,
                note = "telephony not wired for this platform/runtime",
            ),
            network = NetworkCapabilitySnapshot(
                support = CapabilitySupport.PARTIAL,
                note = "network capability capture not wired for this platform/runtime",
            ),
            location = LocationCapabilitySnapshot(
                support = CapabilitySupport.NOT_SUPPORTED,
                note = "location capture not wired for this platform/runtime",
            ),
            device = DeviceCapabilitySnapshot(
                support = CapabilitySupport.PARTIAL,
                note = "device metadata capture not wired for this platform/runtime",
            ),
        )
    }
}

/**
 * Merges best-effort platform capability data into a measurement without clobbering
 * fields that are already present.
 */
class MeasurementCapabilityEnricher {
    fun enrich(
        measurement: Measurement,
        snapshot: PlatformCapabilitySnapshot,
    ): Measurement {
        val telephony = snapshot.telephony
        val network = snapshot.network
        val device = snapshot.device
        val locations = if (measurement.locations.isNullOrEmpty()) snapshot.location.samples else measurement.locations
        val cells = if (measurement.cells.isNullOrEmpty()) telephony.cells else measurement.cells
        return measurement.copy(
            deviceManufacturer = measurement.deviceManufacturer ?: device.manufacturer,
            deviceModel = measurement.deviceModel ?: device.model,
            deviceOsName = measurement.deviceOsName ?: device.osName,
            deviceOsVersion = measurement.deviceOsVersion ?: device.osVersion,
            appVersion = measurement.appVersion ?: device.appVersion,
            provider = measurement.provider ?: telephony.provider,
            simMcc = measurement.simMcc ?: telephony.simMcc,
            simMnc = measurement.simMnc ?: telephony.simMnc,
            netMcc = measurement.netMcc ?: telephony.netMcc,
            netMnc = measurement.netMnc ?: telephony.netMnc,
            networkConnected = measurement.networkConnected ?: network.connected,
            networkAvailable = measurement.networkAvailable ?: network.available,
            networkRoaming = measurement.networkRoaming ?: network.roaming,
            connectionType = measurement.connectionType ?: network.connectionType,
            cellularDataEnabled = measurement.cellularDataEnabled ?: network.cellularDataEnabled,
            locations = locations,
            cells = cells,
        )
    }
}
