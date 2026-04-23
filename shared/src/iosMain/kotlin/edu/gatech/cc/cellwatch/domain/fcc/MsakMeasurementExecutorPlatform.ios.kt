package edu.gatech.cc.cellwatch.domain.fcc

import edu.gatech.cc.cellwatch.domain.capability.MeasurementCapabilityEnricher
import edu.gatech.cc.cellwatch.domain.model.LatencyData
import edu.gatech.cc.cellwatch.domain.model.Measurement
import edu.gatech.cc.cellwatch.domain.model.NetworkConnectionType
import edu.gatech.cc.cellwatch.domain.model.UploadDownloadData
import edu.gatech.cc.cellwatch.msak.shared.Server
import edu.gatech.cc.cellwatch.msak.shared.latency.LatencyConfig
import edu.gatech.cc.cellwatch.msak.shared.latency.runLatency
import edu.gatech.cc.cellwatch.msak.shared.throughput.ThroughputConfig
import edu.gatech.cc.cellwatch.msak.shared.throughput.ThroughputDirection as MsakThroughputDirection
import edu.gatech.cc.cellwatch.msak.shared.throughput.runThroughput
import com.benasher44.uuid.uuid4
import kotlinx.datetime.Clock
import kotlin.math.roundToInt

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
                val summary = runLatency(
                    LatencyConfig(
                        server = server.toMsakServer(),
                        measurementId = id,
                        duration = config.latencyDurationMs,
                        userAgent = config.userAgent,
                    ),
                )
                val now = clock.now()
                val measurement = Measurement(
                    id = id,
                    groupId = groupId,
                    type = "latency",
                    timestamp = now,
                    duration = config.latencyDurationMs * 1_000,
                    success = MeasurementResultPolicy.latencyResultSuccess(summary.received),
                    connectionType = NetworkConnectionType.CELLULAR,
                    cellularDataEnabled = true,
                    latencyData = LatencyData(
                        id = uuid4().toString(),
                        measurementId = id,
                        rtt = summary.meanMs?.roundToInt(),
                        jitter = summary.stdevMs?.roundToInt(),
                        sent = summary.sent,
                        received = summary.received,
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
                val msakDirection = when (direction) {
                    ThroughputDirection.DOWNLOAD -> MsakThroughputDirection.DOWNLOAD
                    ThroughputDirection.UPLOAD -> MsakThroughputDirection.UPLOAD
                }
                val summary = runThroughput(
                    ThroughputConfig(
                        server = server.toMsakServer(),
                        direction = msakDirection,
                        streams = config.throughputStreams,
                        durationMs = config.throughputDurationMs,
                        delayMs = config.throughputDelayMs,
                        userAgent = config.userAgent,
                        measurementId = id,
                    ),
                )
                val now = clock.now()
                val bytesPerSec = (summary.mbps * 1_000_000.0) / 8.0
                val measurement = Measurement(
                    id = id,
                    groupId = groupId,
                    type = prefix,
                    timestamp = now,
                    duration = config.throughputDurationMs * 1_000,
                    success = MeasurementResultPolicy.throughputResultSuccess(bytesPerSec),
                    connectionType = NetworkConnectionType.CELLULAR,
                    cellularDataEnabled = true,
                    uploadDownloadData = UploadDownloadData(
                        id = uuid4().toString(),
                        measurementId = id,
                        duration = config.throughputDurationMs * 1_000,
                        bytes = summary.appBytesTotal,
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

private fun MsakServerEndpoint.toMsakServer(): Server {
    return Server(
        machine = machine,
        location = null,
        urls = urls,
        latencyUdpPort = latencyUdpPort,
    )
}
