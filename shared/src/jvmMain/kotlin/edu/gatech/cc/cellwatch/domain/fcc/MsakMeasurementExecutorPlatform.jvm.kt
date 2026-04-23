package edu.gatech.cc.cellwatch.domain.fcc

import edu.gatech.cc.cellwatch.domain.capability.MeasurementCapabilityEnricher
import edu.gatech.cc.cellwatch.domain.model.LatencyData
import edu.gatech.cc.cellwatch.domain.model.Measurement
import edu.gatech.cc.cellwatch.domain.model.NetworkConnectionType
import edu.gatech.cc.cellwatch.domain.model.UploadDownloadData
import com.benasher44.uuid.uuid4
import kotlinx.datetime.Clock

actual object MsakMeasurementExecutorPlatform {
    actual fun create(
        config: MsakMeasurementExecutorConfig,
        clock: Clock,
    ): MeasurementExecutor {
        val enricher = MeasurementCapabilityEnricher()
        return object : MeasurementExecutor {
            override suspend fun runLatency(
                server: MsakServerEndpoint,
                groupId: String,
                measurementId: String?,
            ): Measurement {
                val id = measurementId ?: uuid4().toString()
                val measurement = Measurement(
                    id = id,
                    groupId = groupId,
                    type = "latency",
                    timestamp = clock.now(),
                    duration = config.latencyDurationMs * 1_000,
                    success = true,
                    connectionType = NetworkConnectionType.CELLULAR,
                    cellularDataEnabled = true,
                    latencyData = LatencyData(
                        id = uuid4().toString(),
                        measurementId = id,
                        rtt = 22,
                        jitter = 2,
                        sent = 30,
                        received = 30,
                        servers = listOf(server.machine),
                    ),
                )
                return runCatching {
                    enricher.enrich(
                        measurement = measurement,
                        snapshot = config.capabilityProvider.captureSnapshot(),
                    )
                }.getOrElse { measurement }
            }

            override suspend fun runThroughput(
                server: MsakServerEndpoint,
                direction: ThroughputDirection,
                groupId: String,
                measurementId: String?,
            ): Measurement {
                val prefix = direction.name.lowercase()
                val id = measurementId ?: uuid4().toString()
                val bytes = if (direction == ThroughputDirection.DOWNLOAD) 8_000_000L else 2_500_000L
                val bytesPerSec = bytes.toDouble() / (config.throughputDurationMs / 1_000.0)
                val measurement = Measurement(
                    id = id,
                    groupId = groupId,
                    type = prefix,
                    timestamp = clock.now(),
                    duration = config.throughputDurationMs * 1_000,
                    success = true,
                    connectionType = NetworkConnectionType.CELLULAR,
                    cellularDataEnabled = true,
                    uploadDownloadData = UploadDownloadData(
                        id = uuid4().toString(),
                        measurementId = id,
                        duration = config.throughputDurationMs * 1_000,
                        bytes = bytes,
                        bytesPerSec = bytesPerSec,
                        servers = listOf(server.machine),
                    ),
                )
                return runCatching {
                    enricher.enrich(
                        measurement = measurement,
                        snapshot = config.capabilityProvider.captureSnapshot(),
                    )
                }.getOrElse { measurement }
            }
        }
    }
}
