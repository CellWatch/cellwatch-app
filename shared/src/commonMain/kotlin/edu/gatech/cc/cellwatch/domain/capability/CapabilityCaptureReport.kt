package edu.gatech.cc.cellwatch.domain.capability

data class CapabilityCaptureReport(
    val telephony: CapabilitySupport,
    val network: CapabilitySupport,
    val location: CapabilitySupport,
    val device: CapabilitySupport,
    val notes: List<String>,
)

object CapabilityCaptureReportFormatter {
    fun fromSnapshot(snapshot: PlatformCapabilitySnapshot): CapabilityCaptureReport {
        return CapabilityCaptureReport(
            telephony = snapshot.telephony.support,
            network = snapshot.network.support,
            location = snapshot.location.support,
            device = snapshot.device.support,
            notes = listOfNotNull(
                snapshot.telephony.note?.let { "telephony:$it" },
                snapshot.network.note?.let { "network:$it" },
                snapshot.location.note?.let { "location:$it" },
                snapshot.device.note?.let { "device:$it" },
            ),
        )
    }

    fun format(report: CapabilityCaptureReport): String {
        val summary = "capabilities(t=${report.telephony},n=${report.network},l=${report.location},d=${report.device})"
        return if (report.notes.isEmpty()) {
            summary
        } else {
            "$summary notes=${report.notes.joinToString(" | ")}"
        }
    }
}
