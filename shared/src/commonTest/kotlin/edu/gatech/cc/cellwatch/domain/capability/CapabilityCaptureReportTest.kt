package edu.gatech.cc.cellwatch.domain.capability

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CapabilityCaptureReportTest {

    @Test
    fun fromSnapshot_andFormat_includeStatesAndNotes() {
        val report = CapabilityCaptureReportFormatter.fromSnapshot(
            PlatformCapabilitySnapshot(
                telephony = TelephonyCapabilitySnapshot(
                    support = CapabilitySupport.PERMISSION_DENIED,
                    note = "missing permission",
                ),
                network = NetworkCapabilitySnapshot(
                    support = CapabilitySupport.PARTIAL,
                    note = "best effort",
                ),
                location = LocationCapabilitySnapshot(
                    support = CapabilitySupport.NOT_SUPPORTED,
                ),
                device = DeviceCapabilitySnapshot(
                    support = CapabilitySupport.AVAILABLE,
                ),
            ),
        )

        assertEquals(CapabilitySupport.PERMISSION_DENIED, report.telephony)
        assertEquals(CapabilitySupport.PARTIAL, report.network)
        assertEquals(2, report.notes.size)
        val formatted = CapabilityCaptureReportFormatter.format(report)
        assertTrue(formatted.contains("capabilities(t=PERMISSION_DENIED,n=PARTIAL,l=NOT_SUPPORTED,d=AVAILABLE)"))
        assertTrue(formatted.contains("telephony:missing permission"))
    }
}
