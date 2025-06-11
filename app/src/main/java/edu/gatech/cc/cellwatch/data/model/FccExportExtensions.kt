package edu.gatech.cc.cellwatch.data.model

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

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
    val tests: TestSet
)

@Serializable
data class TestSet(
    val download: UDSubmissionTest? = null,
    val upload: UDSubmissionTest? = null,
    val latency: LatencySubmissionTest? = null
)

fun MeasurementGroup.toFccSubmissionExport(): FccSubmissionExport? {
    val representative = latency ?: download ?: upload ?: return null
    val submission = submission ?: return null

    return FccSubmissionExport(
        test_id = representative.id,
        device_id = representative.deviceId,
        manufacturer = representative.deviceManufacturer,
        model = representative.deviceModel,
        device_type = submission.deviceType,
        device_imei =  submission.deviceImei,
        operating_system = listOfNotNull(
            representative.deviceOsName,
            representative.deviceOsVersion
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
        environment_code = if (submission.inVehicle == true) 1 else 0, //0 - Outdoor Stationary, 1 - in-vehicle mobile, 2 - Indoors --> we don't allow indoor measurements, so dependent on invehicle
        external_antenna_flag = submission.externalAntenna,
        server_source_ip_address = submission.sourceIp,
        server_source_port = submission.sourcePort,
        server_timestamp = submission.serverTimestamp?.toString(),
        tests = TestSet(
            download = download?.toUDSubmissionTest(),
            upload = upload?.toUDSubmissionTest(),
            latency = latency?.toSubmissionTestLatency()
        )
    )
}

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
    val packets_received: Int? = null
)

@Serializable
data class ExportLocation(
    val timestamp: String,
    val latitude: Double,
    val longitude: Double,
    val horizontal_accuracy: Double? = null,
    val speed: Double? = null,
    val speed_accuracy: Double? = null
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
    val arfcn: Int? = null
)

private fun Float?.isBogusBandwidth(): Boolean {
    return this == null || this == 2147483f || this.toInt() == 2147483
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
        locations = locations?.mapNotNull { loc ->
            val ts = loc.timestamp?.toString() ?: return@mapNotNull null
            ExportLocation(
                timestamp = ts,
                latitude = String.format("%.6f", loc.lat).toDouble(),
                longitude = String.format("%.6f", loc.lon).toDouble(),
                horizontal_accuracy = loc.accuracy,
                speed = loc.speed,
                speed_accuracy = loc.speedAccuracy
            )
        },
        cells = cells?.mapNotNull { cell ->
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
                spectrum_bandwidth = if (!cell.spectrumBandwidth.isBogusBandwidth()) cell.spectrumBandwidth else null,
                arfcn = cell.arfcn
            )
        },
        targets = uploadDownloadData?.servers ?: latencyData?.servers,
        connection_type = when (connectionType?.name?.lowercase()) {
            "cellular", "cell" -> "cell"
            "wifi" -> "wifi"
            else -> "other"
        },
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
        locations = locations?.mapNotNull { loc ->
            val ts = loc.timestamp?.toString() ?: return@mapNotNull null
            ExportLocation(
                timestamp = ts,
                latitude = String.format("%.6f", loc.lat).toDouble(),
                longitude = String.format("%.6f", loc.lon).toDouble(),
                horizontal_accuracy = loc.accuracy,
                speed = loc.speed,
                speed_accuracy = loc.speedAccuracy
            )
        },
        cells = cells?.mapNotNull { cell ->
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
                spectrum_bandwidth = if (!cell.spectrumBandwidth.isBogusBandwidth()) cell.spectrumBandwidth else null,
                arfcn = cell.arfcn
            )
        },
        targets = uploadDownloadData?.servers ?: latencyData?.servers,
        connection_type = when (connectionType?.name?.lowercase()) {
            "cellular", "cell" -> "cell"
            "wifi" -> "wifi"
            else -> "other"
        },
        success_flag = success,
        carrier_aggregation_flag = carrierAggregation,
        network_connected_flag = networkConnected,
        network_available_flag = networkAvailable,
        network_roaming_flag = networkRoaming,
        round_trip_time = latencyData?.rtt,
        jitter = latencyData?.jitter,
        packets_sent = latencyData?.sent,
        packets_received = latencyData?.received
    )
}