package edu.gatech.cc.cellwatch.domain.export

import edu.gatech.cc.cellwatch.domain.model.Measurement
import edu.gatech.cc.cellwatch.domain.model.MeasurementGroup
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * The FCC bulk-submission shape, ported from frozenApp unchanged.
 *
 * Field names are snake_case and deliberately not idiomatic Kotlin: they are
 * the wire format the FCC accepts, and renaming them would change the file.
 * Ported rather than re-derived from the specification so that this produces
 * the same document the previous app did - a difference here is a difference
 * in what gets filed.
 */
@Serializable
data class FccSubmissionExportBundle(
    val contact: ExportContact,
    val submission_category: String,
    val submissions: List<FccSubmissionExport>,
)

@Serializable
data class ExportContact(
    val name: String? = null,
    val email: String? = null,
    val phone: String? = null,
)

@Serializable
data class FccSubmissionExport(
    val test_id: String,
    val device_id: String? = null,
    val manufacturer: String? = null,
    val model: String? = null,
    val device_type: String? = null,
    val operating_system: String? = null,
    val device_imei: String? = null,
    val device_tac: String? = null,
    val app_name: String? = null,
    val app_version: String? = null,
    val device_timestamp: String? = null,
    val sim_mobile_country_code: String? = null,
    val sim_mobile_network_code: String? = null,
    val net_mobile_country_code: String? = null,
    val net_mobile_network_code: String? = null,
    val provider_name: String? = null,
    val scheduled_test_flag: Boolean? = null,
    val in_vehicle_flag: Boolean? = null,
    val environment_code: Int? = null,
    val external_antenna_flag: Boolean? = null,
    val server_source_ip_address: String? = null,
    val server_source_port: Int? = null,
    val server_timestamp: String? = null,
    val tests: TestSet,
)

@Serializable
data class TestSet(
    val download: UDSubmissionTest? = null,
    val upload: UDSubmissionTest? = null,
    val latency: LatencySubmissionTest? = null,
)

@Serializable
data class UDSubmissionTest(
    val timestamp: String,
    val warmup_duration: Long? = null,
    val warmup_bytes_transferred: Long? = null,
    val duration: Long? = null,
    val bytes_transferred: Long? = null,
    val bytes_sec: Int? = null,
    val locations: List<ExportLocation>? = null,
    val cells: List<ExportCell>? = null,
    val targets: List<String>? = null,
    val connection_type: String? = null,
    val success_flag: Boolean? = null,
    val carrier_aggregation_flag: Boolean? = null,
    val network_connected_flag: Boolean? = null,
    val network_available_flag: Boolean? = null,
    val network_roaming_flag: Boolean? = null,
)

@Serializable
data class LatencySubmissionTest(
    val timestamp: String,
    val duration: Long? = null,
    val locations: List<ExportLocation>? = null,
    val cells: List<ExportCell>? = null,
    val targets: List<String>? = null,
    val connection_type: String? = null,
    val success_flag: Boolean? = null,
    val carrier_aggregation_flag: Boolean? = null,
    val network_connected_flag: Boolean? = null,
    val network_available_flag: Boolean? = null,
    val network_roaming_flag: Boolean? = null,
    val round_trip_time: Int? = null,
    val jitter: Int? = null,
    val packets_sent: Int? = null,
    val packets_received: Int? = null,
)

@Serializable
data class ExportLocation(
    val timestamp: String,
    val latitude: String,
    val longitude: String,
    val horizontal_accuracy: Double? = null,
    val speed: Double? = null,
    val speed_accuracy: Double? = null,
)

@Serializable
data class ExportCell(
    val timestamp: String,
    val cell_id: Long? = null,
    val physical_cell_id: Int? = null,
    val cell_connection: Int? = null,
    val network_generation: String? = null,
    val network_subtype: String? = null,
    val signal_strength: Int? = null,
    val rssi: Int? = null,
    val rsrp: Int? = null,
    val rsrq: Int? = null,
    val sinr: Int? = null,
    val csi_rsrp: Int? = null,
    val csi_rsrq: Int? = null,
    val csi_sinr: Int? = null,
    val cqi: Int? = null,
    val spectrum_band: String? = null,
    val spectrum_bandwidth: Float? = null,
    val arfcn: Int? = null,
)

/**
 * Null when the group carries no submission.
 *
 * A group without one was never eligible - off cellular, incomplete, or the
 * user opted out - and the FCC document is for filing, not for describing what
 * was skipped. The extended export is where those appear.
 */
fun MeasurementGroup.toFccSubmissionExport(): FccSubmissionExport? {
    val representative = latency ?: download ?: upload ?: return null
    val submission = submission ?: return null

    return FccSubmissionExport(
        test_id = representative.id,
        device_id = representative.deviceId,
        manufacturer = representative.deviceManufacturer,
        model = representative.deviceModel,
        device_type = submission.deviceType,
        device_imei = submission.deviceImei,
        operating_system = listOfNotNull(
            representative.deviceOsName,
            representative.deviceOsVersion,
        ).joinToString(" "),
        app_name = representative.appName,
        app_version = representative.appVersion,
        device_timestamp = representative.timestamp?.toString(),
        device_tac = submission.deviceTac,
        sim_mobile_country_code = representative.simMcc,
        sim_mobile_network_code = representative.simMnc,
        net_mobile_country_code = representative.netMcc,
        net_mobile_network_code = representative.netMnc,
        provider_name = representative.provider,
        scheduled_test_flag = representative.scheduled,
        in_vehicle_flag = submission.inVehicle,
        // 0 outdoor stationary, 1 in-vehicle mobile, 2 indoors. Indoor
        // measurements are not offered, so this follows the in-vehicle answer.
        environment_code = if (submission.inVehicle == true) 1 else 0,
        external_antenna_flag = submission.externalAntenna,
        server_source_ip_address = submission.sourceIp,
        server_source_port = submission.sourcePort,
        server_timestamp = submission.serverTimestamp?.toString(),
        tests = TestSet(
            download = download?.toUDSubmissionTest(),
            upload = upload?.toUDSubmissionTest(),
            latency = latency?.toSubmissionTestLatency(),
        ),
    )
}

fun Measurement.toUDSubmissionTest(): UDSubmissionTest? {
    val timestampStr = timestamp?.toString() ?: return null
    return UDSubmissionTest(
        timestamp = timestampStr,
        warmup_duration = uploadDownloadData?.warmupDuration,
        warmup_bytes_transferred = uploadDownloadData?.warmupBytes,
        duration = duration,
        bytes_transferred = uploadDownloadData?.bytes,
        bytes_sec = uploadDownloadData?.bytesPerSec?.roundToInt(),
        locations = exportLocations(),
        cells = exportCells(),
        targets = uploadDownloadData?.servers ?: latencyData?.servers,
        connection_type = exportConnectionType(),
        success_flag = success,
        carrier_aggregation_flag = carrierAggregation,
        network_connected_flag = networkConnected,
        network_available_flag = networkAvailable,
        network_roaming_flag = networkRoaming,
    )
}

fun Measurement.toSubmissionTestLatency(): LatencySubmissionTest? {
    val timestampStr = timestamp?.toString() ?: return null
    return LatencySubmissionTest(
        timestamp = timestampStr,
        duration = duration,
        locations = exportLocations(),
        cells = exportCells(),
        targets = uploadDownloadData?.servers ?: latencyData?.servers,
        connection_type = exportConnectionType(),
        success_flag = success,
        carrier_aggregation_flag = carrierAggregation,
        network_connected_flag = networkConnected,
        network_available_flag = networkAvailable,
        network_roaming_flag = networkRoaming,
        round_trip_time = latencyData?.rtt,
        jitter = latencyData?.jitter,
        packets_sent = latencyData?.sent,
        packets_received = latencyData?.received,
    )
}

internal fun Measurement.exportConnectionType(): String =
    when (connectionType?.name?.lowercase()) {
        "cellular", "cell" -> "cell"
        "wifi" -> "wifi"
        else -> "other"
    }

internal fun Measurement.exportLocations(): List<ExportLocation>? = locations?.mapNotNull { loc ->
    val ts = loc.timestamp?.toString() ?: return@mapNotNull null
    ExportLocation(
        timestamp = ts,
        latitude = loc.lat.toFixed6(),
        longitude = loc.lon.toFixed6(),
        horizontal_accuracy = loc.accuracy,
        speed = loc.speed,
        speed_accuracy = loc.speedAccuracy,
    )
}

internal fun Measurement.exportCells(): List<ExportCell>? = cells?.mapNotNull { cell ->
    val ts = cell.timestamp?.toString() ?: return@mapNotNull null
    ExportCell(
        timestamp = ts,
        cell_id = cell.cellId,
        physical_cell_id = cell.physicalCellId,
        cell_connection = cell.cellConnection,
        network_generation = cell.networkGeneration,
        network_subtype = cell.networkSubtype,
        signal_strength = cell.signalStrength,
        rssi = cell.rssi,
        rsrp = cell.rsrp,
        rsrq = cell.rsrq,
        sinr = cell.sinr,
        csi_rsrp = cell.csiRsrp,
        csi_rsrq = cell.csiRsrq,
        csi_sinr = cell.csiSinr,
        cqi = cell.cqi,
        spectrum_band = cell.spectrumBand?.takeIf { it.isNotBlank() },
        spectrum_bandwidth = cell.spectrumBandwidth?.takeUnless { it.isBogusBandwidth() },
        arfcn = cell.arfcn,
    )
}

/** Int.MAX_VALUE leaking through the telephony API as a bandwidth. */
private fun Float.isBogusBandwidth(): Boolean = this == 2147483f || this.toInt() == 2147483

/**
 * Six decimal places, without `String.format` - which is JVM-only and would
 * not compile for iOS.
 */
internal fun Double.toFixed6(): String {
    val scaled = (this * 1_000_000.0).roundToLong()
    val sign = if (scaled < 0) "-" else ""
    val magnitude = abs(scaled)
    val whole = magnitude / 1_000_000
    val fraction = (magnitude % 1_000_000).toString().padStart(6, '0')
    return "$sign$whole.$fraction"
}
