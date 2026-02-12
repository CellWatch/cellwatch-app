package edu.gatech.cc.cellwatch.domain.fcc

import edu.gatech.cc.cellwatch.domain.model.LatencyData
import edu.gatech.cc.cellwatch.domain.model.Measurement
import edu.gatech.cc.cellwatch.domain.model.NetworkConnectionType
import edu.gatech.cc.cellwatch.domain.model.UploadDownloadData
import kotlinx.datetime.Clock

actual object MsakMeasurementExecutorPlatform {
    actual fun create(
        config: MsakMeasurementExecutorConfig,
        clock: Clock,
    ): MeasurementExecutor {
        return object : MeasurementExecutor {
            override suspend fun runLatency(
                server: MsakServerEndpoint,
                groupId: String,
                measurementId: String?,
            ): Measurement {
                val id = measurementId ?: "latency-$groupId"
                return Measurement(
                    id = id,
                    groupId = groupId,
                    type = "latency",
                    timestamp = clock.now(),
                    duration = config.latencyDurationMs * 1_000,
                    success = true,
                    provider = server.machine,
                    connectionType = NetworkConnectionType.CELLULAR,
                    cellularDataEnabled = true,
                    latencyData = LatencyData(
                        id = "latency-data-$id",
                        measurementId = id,
                        rtt = 22,
                        jitter = 2,
                        sent = 30,
                        received = 30,
                        servers = listOf(server.machine),
                    ),
                )
            }

            override suspend fun runThroughput(
                server: MsakServerEndpoint,
                direction: ThroughputDirection,
                groupId: String,
                measurementId: String?,
            ): Measurement {
                val prefix = direction.name.lowercase()
                val id = measurementId?.let { "$prefix-$it" } ?: "$prefix-$groupId"
                val bytes = if (direction == ThroughputDirection.DOWNLOAD) 8_000_000L else 2_500_000L
                val bytesPerSec = bytes.toDouble() / (config.throughputDurationMs / 1_000.0)
                return Measurement(
                    id = id,
                    groupId = groupId,
                    type = prefix,
                    timestamp = clock.now(),
                    duration = config.throughputDurationMs * 1_000,
                    success = true,
                    provider = server.machine,
                    connectionType = NetworkConnectionType.CELLULAR,
                    cellularDataEnabled = true,
                    uploadDownloadData = UploadDownloadData(
                        id = "ud-$id",
                        measurementId = id,
                        duration = config.throughputDurationMs * 1_000,
                        bytes = bytes,
                        bytesPerSec = bytesPerSec,
                        servers = listOf(server.machine),
                    ),
                )
            }
        }
    }
}
