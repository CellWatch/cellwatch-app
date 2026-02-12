package edu.gatech.cc.cellwatch.domain.capability

import edu.gatech.cc.cellwatch.domain.model.Measurement
import edu.gatech.cc.cellwatch.domain.model.NetworkConnectionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlatformCapabilityParityReportTest {

    @Test
    fun normalizedMeasurementFields_androidVsIosHarness_expectedBestEffortDifferences() {
        val base = Measurement(type = "latency")
        val enricher = MeasurementCapabilityEnricher()

        val androidMeasurement = enricher.enrich(base, androidLikeSnapshot())
        val iosMeasurement = enricher.enrich(base, iosLikeSnapshot())

        val androidReport = NormalizedCapabilityParityReport.fromMeasurement(androidMeasurement)
        val iosReport = NormalizedCapabilityParityReport.fromMeasurement(iosMeasurement)

        // Cross-platform parity contract: support-state fields are always present.
        assertEquals("PARTIAL", androidReport.telephonySupport)
        assertEquals("PARTIAL", androidReport.networkSupport)
        assertEquals("UNAVAILABLE", androidReport.locationSupport)
        assertEquals("AVAILABLE", androidReport.deviceSupport)
        assertEquals("NOT_SUPPORTED", iosReport.telephonySupport)
        assertEquals("PARTIAL", iosReport.networkSupport)
        assertEquals("NOT_SUPPORTED", iosReport.locationSupport)
        assertEquals("AVAILABLE", iosReport.deviceSupport)

        // Both platforms should provide usable device identity in the shared measurement.
        assertTrue(androidReport.deviceIdentityPresent)
        assertTrue(iosReport.deviceIdentityPresent)

        // iOS best-effort allowances: telephony/network detail can legitimately be missing.
        assertTrue(androidReport.providerPresent)
        assertFalse(iosReport.providerPresent)
        assertTrue(androidReport.simIdentifiersPresent)
        assertFalse(iosReport.simIdentifiersPresent)
        assertTrue(androidReport.networkConnectionTypePresent)
        assertFalse(iosReport.networkConnectionTypePresent)

        // Both platforms should still emit capability notes for operator visibility.
        assertTrue(androidReport.capabilityNotesPresent)
        assertTrue(iosReport.capabilityNotesPresent)
    }

    private fun androidLikeSnapshot(): PlatformCapabilitySnapshot {
        return PlatformCapabilitySnapshot(
            telephony = TelephonyCapabilitySnapshot(
                support = CapabilitySupport.PARTIAL,
                provider = "CarrierX",
                simMcc = "310",
                simMnc = "260",
                netMcc = "310",
                netMnc = "260",
                note = "best-effort Android telephony snapshot",
            ),
            network = NetworkCapabilitySnapshot(
                support = CapabilitySupport.PARTIAL,
                connected = true,
                available = true,
                roaming = false,
                connectionType = NetworkConnectionType.CELLULAR,
                note = "best-effort Android connectivity snapshot",
            ),
            location = LocationCapabilitySnapshot(
                support = CapabilitySupport.UNAVAILABLE,
                note = "location capture adapter not yet wired for Android provider",
            ),
            device = DeviceCapabilitySnapshot(
                support = CapabilitySupport.AVAILABLE,
                manufacturer = "Google",
                model = "Pixel",
                osName = "Android",
                osVersion = "14",
            ),
        )
    }

    private fun iosLikeSnapshot(): PlatformCapabilitySnapshot {
        return PlatformCapabilitySnapshot(
            telephony = TelephonyCapabilitySnapshot(
                support = CapabilitySupport.NOT_SUPPORTED,
                note = "iOS telephony metadata is best-effort and not fully exposed in shared adapter yet",
            ),
            network = NetworkCapabilitySnapshot(
                support = CapabilitySupport.PARTIAL,
                connected = null,
                available = null,
                roaming = null,
                connectionType = null,
                note = "iOS network capability adapter currently provides best-effort partial data only",
            ),
            location = LocationCapabilitySnapshot(
                support = CapabilitySupport.NOT_SUPPORTED,
                note = "iOS location capture adapter not wired in shared module yet",
            ),
            device = DeviceCapabilitySnapshot(
                support = CapabilitySupport.AVAILABLE,
                manufacturer = "Apple",
                model = "iPhone",
                osName = "iOS",
                osVersion = "18",
                note = "best-effort iOS device metadata snapshot",
            ),
        )
    }
}

private data class NormalizedCapabilityParityReport(
    val telephonySupport: String?,
    val networkSupport: String?,
    val locationSupport: String?,
    val deviceSupport: String?,
    val providerPresent: Boolean,
    val simIdentifiersPresent: Boolean,
    val networkConnectionTypePresent: Boolean,
    val deviceIdentityPresent: Boolean,
    val capabilityNotesPresent: Boolean,
) {
    companion object {
        fun fromMeasurement(measurement: Measurement): NormalizedCapabilityParityReport {
            return NormalizedCapabilityParityReport(
                telephonySupport = measurement.telephonySupport,
                networkSupport = measurement.networkSupport,
                locationSupport = measurement.locationSupport,
                deviceSupport = measurement.deviceSupport,
                providerPresent = !measurement.provider.isNullOrBlank(),
                simIdentifiersPresent = !measurement.simMcc.isNullOrBlank() && !measurement.simMnc.isNullOrBlank(),
                networkConnectionTypePresent = measurement.connectionType != null,
                deviceIdentityPresent = !measurement.deviceOsName.isNullOrBlank() && !measurement.deviceModel.isNullOrBlank(),
                capabilityNotesPresent = !measurement.capabilityNotes.isNullOrBlank(),
            )
        }
    }
}
