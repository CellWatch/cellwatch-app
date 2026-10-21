package edu.gatech.cc.cellwatch.domain.fcc

import edu.gatech.cc.cellwatch.BuildConfig
import edu.gatech.cc.cellwatch.data.model.LatencyData
import edu.gatech.cc.cellwatch.data.model.Measurement
import edu.gatech.cc.cellwatch.data.model.UploadDownloadData
import edu.gatech.cc.cellwatch.domain.telephony.managers.isUsingCarrierAggregation

abstract class MeasurementTest<T: Any>(
    val groupId: String,
    val type: String,
    private val env: MeasurementEnvironment = AndroidMeasurementEnvironment,
) {
    private val TAG = this::class.simpleName

    init {
        if (type !in listOf("upload", "download", "latency")) {
            throw Exception("unimplemented measurement type: $type")
        }
    }

    suspend fun run(): Measurement {
        val beginLocation = env.getLocation()
        val connectionType = env.getConnectionType()
        val cellularDataEnabled = env.isCellularDataEnabled()
        // Each report of the cells the phone can see: one now, then one per change during the test.
        val cellReports = mutableListOf(env.getCells() ?: emptyList())
        val generations = mutableListOf(env.getActiveNetworkGeneration())
        val stopWatching = env.watchCells {
            cellReports.add(it)
            generations.add(env.getActiveNetworkGeneration())
        }
        val result = try { measure() } finally { stopWatching() }
        // The FCC checks the generation at the end of the test too. Cell info callbacks are
        // asynchronous, so a change near the end may not have been reported before we stopped
        // watching; sample it again now rather than seconds later, after the location fix.
        generations.add(env.getActiveNetworkGeneration())
        val endLocation = env.getLocation()

        if (type == "latency" && result !is LatencyResult) {
            throw Exception("expected ${LatencyResult::class.simpleName} result, got ${result::class.simpleName}")
        }

        if ((type == "upload" || type == "download") && result !is ThroughputResult) {
            throw Exception("expected ${ThroughputResult::class.simpleName} result, got ${result::class.simpleName}")
        }

        val resultSuccess = when (result) {
            is ThroughputResult -> result.success
            is LatencyResult -> result.success
            else -> null
        }

        // The FCC requires the test to stay on the same technology generation to be successful.
        val success = when {
            !generations.all { it == generations.first() } -> false
            else -> resultSuccess
        }

        return Measurement(
            groupId = groupId,
            deviceId = env.getDeviceId(),
            deviceManufacturer = env.getDeviceManufacturer(),
            deviceModel = env.getDeviceModel(),
            deviceOsName = "Android",
            deviceOsVersion = env.getDeviceOsVersion(),
            appName = env.getAppName(),
            appVersion = BuildConfig.VERSION_NAME,
            provider = env.getProviderName(),
            type = type,
            scheduled = false,
            cells = cellReports.flatten(),
            locations = listOfNotNull(beginLocation, endLocation),
            simMcc = env.getSimMobileCountryCode(),
            simMnc = env.getSimMobileNetworkCode(),
            netMcc = env.getNetworkMobileCountryCode(),
            netMnc = env.getNetworkMobileNetworkCode(),
            // Per report: across reports the same serving cell repeats, which isn't aggregation.
            carrierAggregation = cellReports.any { isUsingCarrierAggregation(it) },
            networkAvailable = env.isNetworkAvailable(),
            networkConnected = env.isNetworkConnected(),
            networkRoaming = env.isNetworkRoaming(),
            timestamp = when (result) {
                is ThroughputResult -> result.start
                is LatencyResult -> result.start
                else -> null
            },
            duration = when (result) {
                is ThroughputResult -> result.activeMetrics.usecs + result.warmupMetrics.usecs
                is LatencyResult -> result.usecs
                else -> null
            },
            success = success,
            uploadDownloadData = if (result is ThroughputResult) {
                UploadDownloadData(
                    warmupDuration = result.warmupMetrics.usecs,
                    warmupBytes = result.warmupMetrics.bytes,
                    duration = result.activeMetrics.usecs,
                    bytes = result.activeMetrics.bytes,
                    bytesPerSec = result.activeMetrics.bytesPerSec,
                    servers = listOf(result.targetHost),
                )
            } else null,
            latencyData = if (result is LatencyResult) {
                LatencyData(
                    rtt = result.meanRtt,
                    jitter = result.jitter,
                    sent = result.packetsSent,
                    received = result.packetsReceived,
                    servers = listOf(result.targetHost),
                )
            } else null,
            connectionType = connectionType,
            cellularDataEnabled = cellularDataEnabled,
        )
    }

    protected abstract suspend fun measure(): T
}
