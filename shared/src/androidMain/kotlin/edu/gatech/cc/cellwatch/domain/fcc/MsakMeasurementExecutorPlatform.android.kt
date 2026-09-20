package edu.gatech.cc.cellwatch.domain.fcc

import edu.gatech.cc.cellwatch.domain.capability.MeasurementCapabilityEnricher
import edu.gatech.cc.cellwatch.domain.capability.MeasurementObservation
import edu.gatech.cc.cellwatch.domain.capability.mergeInto
import edu.gatech.cc.cellwatch.core.util.runCatchingCancellable
import edu.gatech.cc.cellwatch.domain.model.LatencyData
import edu.gatech.cc.cellwatch.domain.model.Measurement
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
                var observation = MeasurementObservation()
                val observer = config.capabilityProvider.createObserver()
                observer.start()
                val summary = try {
                    runLatency(
                    LatencyConfig(
                        server = server.toMsakServer(),
                        measurementId = id,
                        duration = config.latencyDurationMs,
                        userAgent = config.userAgent,
                    ),
                    )
                } finally {
                    observation = observer.stop()
                }
                val now = clock.now()
                val measurement = Measurement(
                    id = id,
                    groupId = groupId,
                    type = "latency",
                    timestamp = now,
                    // Measured window, not the requested one: termination is driven
                    // by observed server silence, so the real window varies.
                    duration = summary.measuredDurationMs * 1_000,
                    // Generation stability is the second half of the FCC's
                    // success_flag definition; the observation window is what
                    // makes it detectable.
                    success = MeasurementResultPolicy.finalizeMeasurementSuccess(
                        resultSuccess = MeasurementResultPolicy.latencyResultSuccess(
                            packetsReceived = summary.received,
                            measuredDurationMs = summary.measuredDurationMs,
                            requestedDurationMs = config.latencyDurationMs,
                        ),
                        observedGenerations = observation.generations,
                    ),
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
                        // Microseconds, which is what `rtt` means everywhere
                        // else: frozenApp stored it that way, the FCC's
                        // `round_trip_time` is defined as microseconds, and
                        // MeasurementResultReadModelUseCase divides by 1000 to
                        // display it. The MSAK client hands back milliseconds
                        // (`LatencyRunner.summarizeLatency` divides rttUs by
                        // 1000), and assigning that straight across made every
                        // reading a thousand times too small - which is why the
                        // run screen always said "<1 ms" and why every
                        // round_trip_time this app has ever submitted is wrong.
                        rtt = summary.meanMs?.let { (it * 1_000.0).roundToInt() },
                        jitter = summary.stdevMs?.let { (it * 1_000.0).roundToInt() },
                        sent = summary.sent,
                        received = summary.received,
                        servers = listOf(server.machine),
                    ),
                )
                return runCatchingCancellable {
                    enricher.enrich(
                        measurement = observation.mergeInto(measurement),
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
                var observation = MeasurementObservation()
                val observer = config.capabilityProvider.createObserver()
                observer.start()
                val summary = try {
                    runThroughput(
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
                } finally {
                    observation = observer.stop()
                }
                val now = clock.now()
                val bytesPerSec = (summary.mbps * 1_000_000.0) / 8.0
                val measurement = Measurement(
                    id = id,
                    groupId = groupId,
                    type = prefix,
                    timestamp = now,
                    duration = summary.measuredDurationMs * 1_000,
                    success = MeasurementResultPolicy.finalizeMeasurementSuccess(
                        resultSuccess = MeasurementResultPolicy.throughputResultSuccess(
                            activeBytesPerSec = bytesPerSec,
                            measuredDurationMs = summary.measuredDurationMs,
                            requestedDurationMs = config.throughputDurationMs,
                        ),
                        observedGenerations = observation.generations,
                    ),
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
                        warmupDuration = summary.warmupDurationMs * 1_000,
                        warmupBytes = summary.warmupBytesTransferred,
                        duration = summary.measuredDurationMs * 1_000,
                        bytes = summary.appBytesTotal,
                        bytesPerSec = bytesPerSec,
                        servers = listOf(server.machine),
                    ),
                )
                return runCatchingCancellable {
                    enricher.enrich(
                        measurement = observation.mergeInto(measurement),
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
