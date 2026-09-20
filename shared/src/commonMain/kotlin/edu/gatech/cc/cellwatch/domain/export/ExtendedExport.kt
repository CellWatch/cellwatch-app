package edu.gatech.cc.cellwatch.domain.export

import edu.gatech.cc.cellwatch.domain.fcc.FccSubmissionOutcomeMessage
import edu.gatech.cc.cellwatch.domain.model.Measurement
import edu.gatech.cc.cellwatch.domain.model.MeasurementGroup
import edu.gatech.cc.cellwatch.domain.model.NetworkConnectionType
import kotlinx.serialization.Serializable

/**
 * Everything the app recorded, including what the FCC document cannot carry.
 *
 * The BDC export only contains groups that produced a submission. On a device
 * that cannot qualify - no carrier detected, Wi-Fi only, submission switched
 * off - it is an empty file, and every measurement taken is invisible. That is
 * the normal case on an emulator or simulator, and it is also exactly the
 * evidence needed to explain to the FCC what a platform could not capture.
 *
 * So this is not a superset for its own sake: it is the only export that says
 * anything when a measurement did not qualify, and it records *why*.
 */
@Serializable
data class ExtendedExportBundle(
    val schema: String = SCHEMA,
    val exported_at: String,
    val app: ExtendedExportApp,
    val contact: ExportContact,
    val summary: ExtendedExportSummary,
    val runs: List<ExtendedExportRun>,
) {
    companion object {
        /** Versioned, because this shape is ours to change and consumers need to tell. */
        const val SCHEMA = "cellwatch.extended-export.v1"
    }
}

@Serializable
data class ExtendedExportApp(
    val name: String? = null,
    val version: String? = null,
    val device_id: String? = null,
    val platform: String? = null,
    val msak_mode: String? = null,
    val msak_endpoint: String? = null,
    val upload_target: String? = null,
    val collection_mode: String? = null,
)

@Serializable
data class ExtendedExportSummary(
    val run_count: Int,
    val measurement_count: Int,
    val submitted_run_count: Int,
    val unsynced_measurement_count: Int,
)

@Serializable
data class ExtendedExportRun(
    val group_id: String,
    val submitted_to_fcc: Boolean,
    /** Why the run produced no FCC submission, in the app's own words. */
    val fcc_outcome: String? = null,
    val measurements: List<ExtendedExportMeasurement>,
)

@Serializable
data class ExtendedExportMeasurement(
    val id: String,
    val type: String? = null,
    val timestamp: String? = null,
    val success: Boolean? = null,
    val duration_micros: Long? = null,
    val connection_type: String? = null,
    val cellular_data_enabled: Boolean? = null,
    val uploaded_at: String? = null,
    // Capability reporting: the BDC form has no field for "this device would
    // not tell us", which is the single most useful thing when a measurement
    // is thin.
    val telephony_support: String? = null,
    val network_support: String? = null,
    val location_support: String? = null,
    val device_support: String? = null,
    val capability_notes: String? = null,
    val round_trip_time: Int? = null,
    val jitter: Int? = null,
    val packets_sent: Int? = null,
    val packets_received: Int? = null,
    val bytes_transferred: Long? = null,
    val bytes_sec: Double? = null,
    val targets: List<String>? = null,
    val locations: List<ExportLocation>? = null,
    val cells: List<ExportCell>? = null,
)

/**
 * Why this run did or did not produce a submission, from what was stored.
 *
 * Derived rather than remembered: the validation result is transient. What the
 * measurements themselves record - the connection type and the carrier - is
 * enough to name the two commonest causes accurately, and anything else is
 * reported as unrecorded rather than guessed.
 */
fun MeasurementGroup.deriveFccOutcome(challengeMode: Boolean): String {
    if (submission != null) return FccSubmissionOutcomeMessage.SUBMITTED
    if (!challengeMode) return FccSubmissionOutcomeMessage.OPTED_OUT
    val measurements = listOfNotNull(latency, download, upload)
    if (measurements.isEmpty()) return FccSubmissionOutcomeMessage.INCOMPLETE_TESTS
    if (measurements.any { it.connectionType != NetworkConnectionType.CELLULAR }) {
        return FccSubmissionOutcomeMessage.NOT_ELIGIBLE
    }
    if (measurements.all { it.provider.isNullOrBlank() }) {
        return FccSubmissionOutcomeMessage.CARRIER_UNKNOWN
    }
    return FccSubmissionOutcomeMessage.REASON_UNRECORDED
}

fun MeasurementGroup.toExtendedExportRun(fccOutcome: String?): ExtendedExportRun =
    ExtendedExportRun(
        group_id = id,
        submitted_to_fcc = submission != null,
        fcc_outcome = fccOutcome,
        measurements = listOfNotNull(latency, download, upload).map { it.toExtendedExportMeasurement() },
    )

fun Measurement.toExtendedExportMeasurement(): ExtendedExportMeasurement =
    ExtendedExportMeasurement(
        id = id,
        type = type,
        timestamp = timestamp?.toString(),
        success = success,
        duration_micros = duration,
        connection_type = connectionType?.name,
        cellular_data_enabled = cellularDataEnabled,
        uploaded_at = uploadTime?.toString(),
        telephony_support = telephonySupport,
        network_support = networkSupport,
        location_support = locationSupport,
        device_support = deviceSupport,
        capability_notes = capabilityNotes,
        round_trip_time = latencyData?.rtt,
        jitter = latencyData?.jitter,
        packets_sent = latencyData?.sent,
        packets_received = latencyData?.received,
        bytes_transferred = uploadDownloadData?.bytes,
        bytes_sec = uploadDownloadData?.bytesPerSec,
        targets = uploadDownloadData?.servers ?: latencyData?.servers,
        locations = exportLocations(),
        cells = exportCells(),
    )
