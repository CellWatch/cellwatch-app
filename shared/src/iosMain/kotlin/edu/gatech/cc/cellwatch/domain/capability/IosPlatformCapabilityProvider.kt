package edu.gatech.cc.cellwatch.domain.capability

import com.benasher44.uuid.uuid4
import edu.gatech.cc.cellwatch.domain.model.Cell
import edu.gatech.cc.cellwatch.core.util.SharedLog
import edu.gatech.cc.cellwatch.domain.model.Location
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.toKotlinInstant
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
import platform.CoreTelephony.CTServiceRadioAccessTechnologyDidChangeNotification
import platform.CoreTelephony.CTTelephonyNetworkInfo
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.darwin.NSObjectProtocol
import platform.UIKit.UIDevice

@OptIn(ExperimentalForeignApi::class)
class IosPlatformCapabilityProvider(
    private val clock: Clock = Clock.System,
) : PlatformCapabilityProvider {
    override suspend fun captureSnapshot(): PlatformCapabilitySnapshot {
        val device = UIDevice.currentDevice
        val capturedAt = clock.now()
        val locationSnapshot = captureLocationSnapshot(capturedAt)
        val telephonySnapshot = captureTelephonySnapshot()
        return PlatformCapabilitySnapshot(
            capturedAt = capturedAt,
            telephony = telephonySnapshot,
            network = captureNetworkSnapshot(),
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

    /**
     * Reports the interface the system would actually use, so a measurement
     * taken over WiFi or USB tethering is not recorded as cellular.
     *
     * Leaves connectionType null when the path cannot be read, rather than
     * guessing: FccSubmissionPolicy requires CELLULAR, so unknown correctly
     * makes a measurement non-submittable instead of silently passing.
     */
    private suspend fun captureNetworkSnapshot(): NetworkCapabilitySnapshot {
        val path = readIosNetworkPath()
            ?: run {
                // Diagnostic: the interface decides whether a measurement is
                // submittable, and it was previously recorded only in the
                // persisted note - invisible while field testing.
                SharedLog.w(LOG_TAG, "network path unavailable; connection type unknown")
                return NetworkCapabilitySnapshot(
                    support = CapabilitySupport.UNAVAILABLE,
                    note = "network path unavailable; connection type could not be determined",
                )
            }
        SharedLog.d(
            LOG_TAG,
            "interface=${path.describedInterface} connectionType=${path.connectionType} " +
                "satisfied=${path.satisfied} cellular=${path.usesCellular} " +
                "wifi=${path.usesWifi} wired=${path.usesWired}",
        )

        return NetworkCapabilitySnapshot(
            support = CapabilitySupport.AVAILABLE,
            connected = path.satisfied,
            available = path.satisfied,
            // iOS exposes no roaming indicator to third-party apps.
            roaming = null,
            connectionType = path.connectionType,
            // Cellular being the active interface proves cellular data is
            // usable. When it is not active, iOS gives no reliable way to tell
            // whether it is merely unused or actually disabled, so say nothing.
            cellularDataEnabled = if (path.usesCellular) true else null,
            note = "nw_path interface: ${path.describedInterface}",
        )
    }

    private companion object {
        const val LOG_TAG = "IosCapability"
    }

    override fun createObserver(): MeasurementObserver = IosMeasurementObserver()

    /**
     * Best-effort observation for one measurement on iOS.
     *
     * iOS gives third-party apps no cell enumeration, no cell identity and no
     * signal measurements - only the current radio access technology. But the
     * BDC iOS exemption list (v2.2 section 3.1.1) exempts fields WITHIN a Cell
     * Object, not the cells array itself, and exempts neither
     * network_generation nor network_subtype. So the correct output is a sparse
     * Cell Object carrying what CoreTelephony does know, rather than an empty
     * array. Recorded as discrepancy 1 in doc/FCC_IOS_DISCREPANCIES.md.
     *
     * Radio changes are observed via CoreTelephony's notification, which is
     * coarser than Android's per-cell callback and can lag a real handover. It
     * therefore under-reports generation changes rather than inventing them,
     * which is the safe direction for success_flag.
     */
    private inner class IosMeasurementObserver : MeasurementObserver {
        private val cells = mutableListOf<Cell>()
        private val generations = mutableListOf<String>()
        private val locations = mutableListOf<Location>()
        private var notificationObserver: NSObjectProtocol? = null

        override suspend fun start() {
            runCatching { locations += captureLocationSnapshot(clock.now()).samples }
            sampleRadio()
            notificationObserver = NSNotificationCenter.defaultCenter.addObserverForName(
                name = CTServiceRadioAccessTechnologyDidChangeNotification,
                `object` = null,
                queue = NSOperationQueue.mainQueue,
            ) { _ -> sampleRadio() }
        }

        override suspend fun stop(): MeasurementObservation {
            notificationObserver?.let { NSNotificationCenter.defaultCenter.removeObserver(it) }
            notificationObserver = null
            sampleRadio()
            runCatching { locations += captureLocationSnapshot(clock.now()).samples }
            return MeasurementObservation(
                cells = cells.toList(),
                generations = generations.toList(),
                locations = locations.toList(),
                note = "iOS exposes no cell identity or signal detail; " +
                    "cells carry timestamp, generation and subtype only",
            )
        }

        /**
         * One sparse Cell Object per observation, plus the generation sample
         * that success_flag depends on.
         */
        private fun sampleRadio() {
            val radioAccess = CTTelephonyNetworkInfo().currentRadioAccessTechnology
            if (radioAccess.isNullOrBlank()) return
            val now = clock.now()
            val generation = networkGeneration(radioAccess)
            generation?.let { generations += it }
            cells += Cell(
                id = uuid4().toString(),
                timestamp = now,
                // Everything below is on the FCC's iOS exemption list; iOS
                // genuinely cannot supply any of it.
                cellId = null,
                physicalCellId = null,
                cellConnection = null,
                networkGeneration = generation,
                networkSubtype = networkSubtype(radioAccess),
                signalStrength = null,
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
                measurementId = null,
                createdOn = now,
                updatedOn = now,
            )
        }
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

    private fun captureLocationSnapshot(capturedAt: Instant): LocationCapabilitySnapshot {
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
                        // CLLocation's own fix time. This was null, and
                        // `locations.timestamp` is NOT NULL server-side, so
                        // every iOS measurement carrying a location was
                        // rejected by insert_measurement - silently, because a
                        // failed upload only ever showed as "Pending sync".
                        timestamp = location.timestamp.toKotlinInstant(),
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
                                // Falls back to the capture time rather than
                                // staying null: a location row the server will
                                // reject is worse than one with an approximate
                                // time, and the bridge sample may not carry one.
                                timestamp = latest.timestamp ?: capturedAt,
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
