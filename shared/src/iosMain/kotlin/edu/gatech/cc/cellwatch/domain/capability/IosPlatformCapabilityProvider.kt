package edu.gatech.cc.cellwatch.domain.capability

import edu.gatech.cc.cellwatch.domain.model.Location
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.datetime.Clock
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusDenied
import platform.CoreLocation.kCLAuthorizationStatusNotDetermined
import platform.CoreLocation.kCLAuthorizationStatusRestricted
import platform.CoreTelephony.CTRadioAccessTechnologyCDMA1x
import platform.CoreTelephony.CTRadioAccessTechnologyCDMAEVDORev0
import platform.CoreTelephony.CTRadioAccessTechnologyCDMAEVDORevA
import platform.CoreTelephony.CTRadioAccessTechnologyCDMAEVDORevB
import platform.CoreTelephony.CTRadioAccessTechnologyEdge
import platform.CoreTelephony.CTRadioAccessTechnologyGPRS
import platform.CoreTelephony.CTRadioAccessTechnologyHSDPA
import platform.CoreTelephony.CTRadioAccessTechnologyHSUPA
import platform.CoreTelephony.CTRadioAccessTechnologyLTE
import platform.CoreTelephony.CTRadioAccessTechnologyNR
import platform.CoreTelephony.CTRadioAccessTechnologyNRNSA
import platform.CoreTelephony.CTRadioAccessTechnologyWCDMA
import platform.CoreTelephony.CTTelephonyNetworkInfo
import platform.UIKit.UIDevice

@OptIn(ExperimentalForeignApi::class)
class IosPlatformCapabilityProvider(
    private val clock: Clock = Clock.System,
) : PlatformCapabilityProvider {
    override suspend fun captureSnapshot(): PlatformCapabilitySnapshot {
        val device = UIDevice.currentDevice
        val locationSnapshot = captureLocationSnapshot()
        val telephonySnapshot = captureTelephonySnapshot()
        return PlatformCapabilitySnapshot(
            capturedAt = clock.now(),
            telephony = telephonySnapshot,
            network = NetworkCapabilitySnapshot(
                support = CapabilitySupport.PARTIAL,
                note = "iOS network capability adapter currently provides best-effort partial data only",
            ),
            location = locationSnapshot,
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

    private fun captureTelephonySnapshot(): TelephonyCapabilitySnapshot {
        val radioAccess = CTTelephonyNetworkInfo().currentRadioAccessTechnology
        return if (radioAccess.isNullOrBlank()) {
            TelephonyCapabilitySnapshot(
                support = CapabilitySupport.NOT_SUPPORTED,
                note = "iOS telephony metadata is heavily limited; no radio access technology is available from CoreTelephony",
            )
        } else {
            TelephonyCapabilitySnapshot(
                support = CapabilitySupport.PARTIAL,
                networkGeneration = networkGeneration(radioAccess),
                networkSubtype = networkSubtype(radioAccess),
                note = "best-effort iOS radio access technology from CoreTelephony; carrier, MCC/MNC, and cell detail remain unavailable",
            )
        }
    }

    private fun captureLocationSnapshot(): LocationCapabilitySnapshot {
        if (!CLLocationManager.locationServicesEnabled()) {
            return LocationCapabilitySnapshot(
                support = CapabilitySupport.UNAVAILABLE,
                note = "iOS location services are disabled",
            )
        }

        return when (val status = CLLocationManager.authorizationStatus()) {
            kCLAuthorizationStatusDenied,
            kCLAuthorizationStatusRestricted,
            kCLAuthorizationStatusNotDetermined -> LocationCapabilitySnapshot(
                support = CapabilitySupport.PERMISSION_DENIED,
                note = "iOS location permission is not granted (${statusDescription(status)})",
            )

            kCLAuthorizationStatusAuthorizedAlways,
            kCLAuthorizationStatusAuthorizedWhenInUse -> {
                val sample = IosLocationSampleBridge.currentSample()
                val manager = CLLocationManager()
                val location = manager.location
                val latest = sample ?: location?.coordinate?.useContents {
                    IosLocationSample(
                        timestamp = null,
                        lat = latitude,
                        lon = longitude,
                        accuracy = location.horizontalAccuracy.takeIf { it >= 0.0 },
                        speed = location.speed.takeIf { it >= 0.0 },
                        speedAccuracy = location.speedAccuracy.takeIf { it >= 0.0 },
                        heading = location.course.takeIf { it >= 0.0 },
                    )
                }
                if (latest == null) {
                    LocationCapabilitySnapshot(
                        support = CapabilitySupport.PARTIAL,
                        note = "iOS location permission granted but no current location sample is available yet",
                    )
                } else {
                    LocationCapabilitySnapshot(
                        support = CapabilitySupport.PARTIAL,
                        samples = listOf(
                            Location(
                                timestamp = latest.timestamp,
                                lat = latest.lat,
                                lon = latest.lon,
                                accuracy = latest.accuracy,
                                speed = latest.speed,
                                speedAccuracy = latest.speedAccuracy,
                                heading = latest.heading,
                            ),
                        ),
                        note = if (sample != null) {
                            "best-effort iOS location snapshot from active CLLocationManager delegate updates"
                        } else {
                            "best-effort iOS location snapshot from CLLocationManager"
                        },
                    )
                }
            }

            else -> LocationCapabilitySnapshot(
                support = CapabilitySupport.UNAVAILABLE,
                note = "iOS location authorization state is unknown",
            )
        }
    }

    private fun statusDescription(status: platform.CoreLocation.CLAuthorizationStatus): String {
        return when (status) {
            kCLAuthorizationStatusNotDetermined -> "notDetermined"
            kCLAuthorizationStatusRestricted -> "restricted"
            kCLAuthorizationStatusDenied -> "denied"
            kCLAuthorizationStatusAuthorizedWhenInUse -> "authorizedWhenInUse"
            kCLAuthorizationStatusAuthorizedAlways -> "authorizedAlways"
            else -> "unknown"
        }
    }

    private fun networkGeneration(radioAccess: String): String? = when (radioAccess) {
        CTRadioAccessTechnologyGPRS,
        CTRadioAccessTechnologyEdge,
        CTRadioAccessTechnologyCDMA1x,
        CTRadioAccessTechnologyCDMAEVDORev0,
        CTRadioAccessTechnologyCDMAEVDORevA,
        CTRadioAccessTechnologyCDMAEVDORevB -> "3G_OR_LOWER"

        CTRadioAccessTechnologyWCDMA,
        CTRadioAccessTechnologyHSDPA,
        CTRadioAccessTechnologyHSUPA -> "3G"

        CTRadioAccessTechnologyLTE -> "4G"
        CTRadioAccessTechnologyNRNSA,
        CTRadioAccessTechnologyNR -> "5G"

        else -> null
    }

    private fun networkSubtype(radioAccess: String): String? = when (radioAccess) {
        CTRadioAccessTechnologyGPRS -> "GPRS"
        CTRadioAccessTechnologyEdge -> "EDGE"
        CTRadioAccessTechnologyWCDMA -> "WCDMA"
        CTRadioAccessTechnologyHSDPA -> "HSDPA"
        CTRadioAccessTechnologyHSUPA -> "HSUPA"
        CTRadioAccessTechnologyCDMA1x -> "CDMA1x"
        CTRadioAccessTechnologyCDMAEVDORev0 -> "CDMAEVDORev0"
        CTRadioAccessTechnologyCDMAEVDORevA -> "CDMAEVDORevA"
        CTRadioAccessTechnologyCDMAEVDORevB -> "CDMAEVDORevB"
        CTRadioAccessTechnologyLTE -> "LTE"
        CTRadioAccessTechnologyNRNSA -> "NRNSA"
        CTRadioAccessTechnologyNR -> "NR"
        else -> radioAccess
    }
}
