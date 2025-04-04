package edu.gatech.cc.cellwatch.data.model

import edu.gatech.cc.cellwatch.data.model.*
import edu.gatech.cc.cellwatch.domain.telephony.managers.NetworkConnectionType
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

@Serializable
data class FccSubmissionExport(
    val test_id: String,
    val submission_category: String?,
    val contact_name: String?,
    val contact_email: String?,
    val contact_phone: String?,
    val source_ip: String?,
    val source_port: Int?,
    val in_vehicle_flag: Boolean? = false,
    val environment_code: Int? = null,
    val external_antenna_flag: Boolean? = null,
    val scheduled_test_flag: Boolean? = null,
    val tests: TestObject
)

@Serializable
data class TestObject(
    val download: DownloadTest? = null,
    val upload: UploadTest? = null,
    val latency: LatencyTest? = null
)

@Serializable
data class DownloadTest(
    val timestamp: String,
    val warmup_duration: Long?,
    val warmup_bytes_transferred: Long?,
    val duration: Long?,
    val bytes_transferred: Long?,
    val bytes_sec: Int?,
    val locations: List<LocationObject>?,
    val cells: List<Cell>?,
    val targets: List<String>?,
    val connection_type: String? = "cell",
    val success_flag: Boolean?,
    val carrier_aggregation_flag: Boolean?,
    val network_connected_flag: Boolean?,
    val network_available_flag: Boolean?,
    val network_roaming_flag: Boolean?
)

@Serializable
data class UploadTest(
    val timestamp: String,
    val warmup_duration: Long?,
    val warmup_bytes_transferred: Long?,
    val duration: Long?,
    val bytes_transferred: Long?,
    val bytes_sec: Int?,
    val locations: List<LocationObject>?,
    val cells: List<Cell>?,
    val targets: List<String>?,
    val connection_type: String? = "cell",
    val success_flag: Boolean?,
    val carrier_aggregation_flag: Boolean?,
    val network_connected_flag: Boolean?,
    val network_available_flag: Boolean?,
    val network_roaming_flag: Boolean?
)

@Serializable
data class LatencyTest(
    val timestamp: String,
    val duration: Long?,
    val round_trip_time: Int?,
    val jitter: Int?,
    val packets_sent: Int?,
    val packets_received: Int?,
    val locations: List<LocationObject>?,
    val cells: List<Cell>?,
    val targets: List<String>?,
    val connection_type: String? = "cell",
    val success_flag: Boolean?,
    val carrier_aggregation_flag: Boolean?,
    val network_connected_flag: Boolean?,
    val network_available_flag: Boolean?,
    val network_roaming_flag: Boolean?
)

@Serializable
data class LocationObject(
    val timestamp: String,
    val latitude: Double,
    val longitude: Double,
    val horizontal_accuracy: Double? = null,
    val speed: Double? = null,
    val speed_accuracy: Double? = null
)

fun MeasurementGroup.toFccSubmissionExport(): FccSubmissionExport? {
    val submission = submission ?: return null

    fun List<Location>?.toLocationObjects(): List<LocationObject>? {
        return this?.mapNotNull {
            val ts = it.timestamp ?: return@mapNotNull null
            LocationObject(
                timestamp = ts.toString(),
                latitude = it.lat,
                longitude = it.lon,
                horizontal_accuracy = it.accuracy,
                speed = it.speed,
                speed_accuracy = it.speedAccuracy
            )
        }
    }

    fun UploadDownloadData?.toDownloadTest(
        timestamp: String?,
        locations: List<Location>?,
        cells: List<Cell>?,
        success: Boolean?,
        connectionType: NetworkConnectionType?,
        carrierAggregation: Boolean?,
        netConnected: Boolean?,
        netAvailable: Boolean?,
        netRoaming: Boolean?
    ): DownloadTest? {
        if (timestamp == null) return null
        return DownloadTest(
            timestamp = timestamp,
            warmup_duration = this?.warmupDuration,
            warmup_bytes_transferred = this?.warmupBytes,
            duration = this?.duration,
            bytes_transferred = this?.bytes,
            bytes_sec = this?.bytesPerSec?.roundToInt(),
            locations = locations.toLocationObjects(),
            cells = cells,
            targets = this?.servers,
            success_flag = success,
            connection_type = connectionType?.name?.lowercase(),
            carrier_aggregation_flag = carrierAggregation,
            network_connected_flag = netConnected,
            network_available_flag = netAvailable,
            network_roaming_flag = netRoaming
        )
    }

    fun UploadDownloadData?.toUploadTest(
        timestamp: String?,
        locations: List<Location>?,
        cells: List<Cell>?,
        success: Boolean?,
        connectionType: NetworkConnectionType?,
        carrierAggregation: Boolean?,
        netConnected: Boolean?,
        netAvailable: Boolean?,
        netRoaming: Boolean?
    ): UploadTest? {
        if (timestamp == null) return null
        return UploadTest(
            timestamp = timestamp,
            warmup_duration = this?.warmupDuration,
            warmup_bytes_transferred = this?.warmupBytes,
            duration = this?.duration,
            bytes_transferred = this?.bytes,
            bytes_sec = this?.bytesPerSec?.roundToInt(),
            locations = locations.toLocationObjects(),
            cells = cells,
            targets = this?.servers,
            success_flag = success,
            connection_type = connectionType?.name?.lowercase(),
            carrier_aggregation_flag = carrierAggregation,
            network_connected_flag = netConnected,
            network_available_flag = netAvailable,
            network_roaming_flag = netRoaming
        )
    }


    fun LatencyData?.toTest(
        timestamp: String?,
        locations: List<Location>?,
        cells: List<Cell>?,
        success: Boolean?,
        connectionType: NetworkConnectionType?,
        carrierAggregation: Boolean?,
        netConnected: Boolean?,
        netAvailable: Boolean?,
        netRoaming: Boolean?
    ): LatencyTest? {
        if (timestamp == null) return null
        return LatencyTest(
            timestamp = timestamp,
            duration = this@toTest?.let { latency?.duration },
            round_trip_time = this@toTest?.rtt,
            jitter = this@toTest?.jitter,
            packets_sent = this@toTest?.sent,
            packets_received = this@toTest?.received,
            locations = locations.toLocationObjects(),
            cells = cells,
            targets = this@toTest?.servers,
            success_flag = success,
            connection_type = connectionType?.name?.lowercase(),
            carrier_aggregation_flag = carrierAggregation,
            network_connected_flag = netConnected,
            network_available_flag = netAvailable,
            network_roaming_flag = netRoaming
        )
    }

    return FccSubmissionExport(
        test_id = id,
        submission_category = submission.submission,
        contact_name = submission.contactName,
        contact_email = submission.contactEmail,
        contact_phone = submission.contactPhone,
        source_ip = submission.sourceIp,
        source_port = submission.sourcePort,
        in_vehicle_flag = submission.inVehicle,
        environment_code = null, // You may update this from a flag or constant
        external_antenna_flag = submission.externalAntenna,
        scheduled_test_flag = latency?.scheduled ?: download?.scheduled ?: upload?.scheduled,
        tests = TestObject(
            download = download?.uploadDownloadData?.toDownloadTest(
                timestamp = download.timestamp?.toString(),
                locations = download.locations,
                cells = download.cells,
                success = download.success,
                connectionType = download.connectionType,
                carrierAggregation = download.carrierAggregation,
                netConnected = download.networkConnected,
                netAvailable = download.networkAvailable,
                netRoaming = download.networkRoaming
            ),
            upload = upload?.uploadDownloadData?.toUploadTest(
                timestamp = upload.timestamp?.toString(),
                locations = upload.locations,
                cells = upload.cells,
                success = upload.success,
                connectionType = upload.connectionType,
                carrierAggregation = upload.carrierAggregation,
                netConnected = upload.networkConnected,
                netAvailable = upload.networkAvailable,
                netRoaming = upload.networkRoaming
            ),
            latency = latency?.latencyData?.toTest(
                timestamp = latency.timestamp?.toString(),
                locations = latency.locations,
                cells = latency.cells,
                success = latency.success,
                connectionType = latency.connectionType,
                carrierAggregation = latency.carrierAggregation,
                netConnected = latency.networkConnected,
                netAvailable = latency.networkAvailable,
                netRoaming = latency.networkRoaming
            )
        )
    )
}

@Serializable
data class FccExportBundle(
    val fcc_valid: List<FccSubmissionExport>,
    val others: List<MeasurementGroup>
)
