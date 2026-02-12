package edu.gatech.cc.cellwatch.domain.capability

import kotlinx.datetime.Clock
import platform.UIKit.UIDevice

class IosPlatformCapabilityProvider(
    private val clock: Clock = Clock.System,
) : PlatformCapabilityProvider {
    override suspend fun captureSnapshot(): PlatformCapabilitySnapshot {
        val device = UIDevice.currentDevice
        return PlatformCapabilitySnapshot(
            capturedAt = clock.now(),
            telephony = TelephonyCapabilitySnapshot(
                support = CapabilitySupport.NOT_SUPPORTED,
                note = "iOS telephony metadata is best-effort and not fully exposed in shared adapter yet",
            ),
            network = NetworkCapabilitySnapshot(
                support = CapabilitySupport.PARTIAL,
                note = "iOS network capability adapter currently provides best-effort partial data only",
            ),
            location = LocationCapabilitySnapshot(
                support = CapabilitySupport.NOT_SUPPORTED,
                note = "iOS location capture adapter not wired in shared module yet",
            ),
            device = DeviceCapabilitySnapshot(
                support = CapabilitySupport.AVAILABLE,
                manufacturer = "Apple",
                model = device.model,
                osName = device.systemName,
                osVersion = device.systemVersion,
                note = "best-effort iOS device metadata snapshot",
            ),
        )
    }
}
