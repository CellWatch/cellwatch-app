package edu.gatech.cc.cellwatch.domain.fcc

import edu.gatech.cc.cellwatch.domain.capability.MeasurementCapabilityEnricher
import edu.gatech.cc.cellwatch.core.util.runCatchingCancellable
import edu.gatech.cc.cellwatch.domain.model.LatencyData
import edu.gatech.cc.cellwatch.domain.model.Measurement
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
                    // Left null deliberately: the real values come from the
                    // capability snapshot via MeasurementCapabilityEnricher,
                    // which fills only absent fields. Hardcoding CELLULAR here
                    // meant the detected type could never override it, so a
                    // measurement over WiFi or USB tethering was still recorded
                    // as cellular - and FccSubmissionPolicy's cellular check
                    // could never fail.
                    connectionType = null,
                    cellularDataEnabled = null,
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
                return runCatchingCancellable {
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
                    // Left null deliberately: the real values come from the
                    // capability snapshot via MeasurementCapabilityEnricher,
                    // which fills only absent fields. Hardcoding CELLULAR here
                    // meant the detected type could never override it, so a
                    // measurement over WiFi or USB tethering was still recorded
                    // as cellular - and FccSubmissionPolicy's cellular check
                    // could never fail.
                    connectionType = null,
                    cellularDataEnabled = null,
                    uploadDownloadData = UploadDownloadData(
                        id = uuid4().toString(),
                        measurementId = id,
                        warmupDuration = 0L,
                        warmupBytes = 0L,
                        duration = config.throughputDurationMs * 1_000,
                        bytes = bytes,
                        bytesPerSec = bytesPerSec,
                        servers = listOf(server.machine),
                    ),
                )
                return runCatchingCancellable {
                    enricher.enrich(
                        measurement = measurement,
                        snapshot = config.capabilityProvider.captureSnapshot(),
                    )
                }.getOrElse { measurement }
            }
        }
    }
}
