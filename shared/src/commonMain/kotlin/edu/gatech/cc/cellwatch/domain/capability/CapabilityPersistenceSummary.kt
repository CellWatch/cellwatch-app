package edu.gatech.cc.cellwatch.domain.capability

import edu.gatech.cc.cellwatch.domain.model.Measurement

data class CapabilityPersistenceSummary(
    val totalMeasurements: Int,
    val withSupportStates: Int,
    val withNotes: Int,
)

object CapabilityPersistenceSummaryFormatter {
    fun summarize(measurements: List<Measurement>): CapabilityPersistenceSummary {
        val withSupport = measurements.count { it.hasCapabilitySupportStates() }
        val withNotes = measurements.count { !it.capabilityNotes.isNullOrBlank() }
        return CapabilityPersistenceSummary(
            totalMeasurements = measurements.size,
            withSupportStates = withSupport,
            withNotes = withNotes,
        )
    }

    fun format(summary: CapabilityPersistenceSummary): String {
        return "capabilityPersistence(total=${summary.totalMeasurements}, supportStates=${summary.withSupportStates}, notes=${summary.withNotes})"
    }
}

fun Measurement.hasCapabilitySupportStates(): Boolean {
    return !telephonySupport.isNullOrBlank() &&
        !networkSupport.isNullOrBlank() &&
        !locationSupport.isNullOrBlank() &&
        !deviceSupport.isNullOrBlank()
}
