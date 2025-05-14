package edu.gatech.cc.cellwatch.data.model

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

@Serializable
data class FccSubmissionExport(
    val test_id: String,
    val device_id: String? = null,
    val manufacturer: String? = null,
    val model: String? = null,
    val device_type: String? = "Android",
    val operating_system: String? = null,
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
    val external_antenna_flag: Boolean? = null,
    val server_source_ip_address: String? = null,
    val server_source_port: Int? = null,
    val server_timestamp: String? = null,
    val tests: TestSet
)

@Serializable
data class TestSet(
    val download: SubmissionTest? = null,
    val upload: SubmissionTest? = null,
    val latency: SubmissionTest? = null
)

fun MeasurementGroup.toFccSubmissionExport(): FccSubmissionExport? {
    val representative = latency ?: download ?: upload ?: return null
    val submission = submission ?: return null

    return FccSubmissionExport(
        test_id = representative.id,
        device_id = representative.deviceId,
        manufacturer = representative.deviceManufacturer,
        model = representative.deviceModel,
        device_type = "Android",
        operating_system = listOfNotNull(
            representative.deviceOsName,
            representative.deviceOsVersion
        ).joinToString(" "),
        app_name = representative.appName,
        app_version = representative.appVersion,
        device_timestamp = representative.timestamp?.toString(),
        sim_mobile_country_code = representative.simMcc,
        sim_mobile_network_code = representative.simMnc,
        net_mobile_country_code = representative.netMcc,
        net_mobile_network_code = representative.netMnc,
        provider_name = representative.provider,
        scheduled_test_flag = representative.scheduled,
        in_vehicle_flag = submission.inVehicle,
        external_antenna_flag = submission.externalAntenna,
        server_source_ip_address = submission.sourceIp,
        server_source_port = submission.sourcePort,
        server_timestamp = null, // fill in if available
        tests = TestSet(
            download = download?.toSubmissionTest(),
            upload = upload?.toSubmissionTest(),
            latency = latency?.toSubmissionTest()
        )
    )
}

@Serializable
data class SubmissionTest(
    val timestamp: String,
    val warmup_duration: Long? = null,
    val warmup_bytes_transferred: Long? = null,
    val duration: Long? = null,
    val bytes_transferred: Long? = null,
    val bytes_sec: Int? = null,
    val locations: List<Location>? = null,
    val cells: List<Cell>? = null,
    val targets: List<String>? = null,
    val connection_type: String? = "cell",
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

fun Measurement.toSubmissionTest(): SubmissionTest? {
    val timestampStr = timestamp?.toString() ?: return null

    return SubmissionTest(
        timestamp = timestampStr,
        warmup_duration = uploadDownloadData?.warmupDuration,
        warmup_bytes_transferred = uploadDownloadData?.warmupBytes,
        duration = duration,
        bytes_transferred = uploadDownloadData?.bytes,
        bytes_sec = uploadDownloadData?.bytesPerSec?.roundToInt(),
        locations = locations,
        cells = cells,
        targets = uploadDownloadData?.servers ?: latencyData?.servers,
        success_flag = success,
        connection_type = connectionType?.name?.lowercase(),
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