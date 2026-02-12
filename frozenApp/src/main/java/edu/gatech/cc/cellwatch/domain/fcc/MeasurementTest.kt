package edu.gatech.cc.cellwatch.domain.fcc

import android.os.Build
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.Granularity
import com.google.android.gms.location.LocationServices
import edu.gatech.cc.cellwatch.BuildConfig
import edu.gatech.cc.cellwatch.CellWatchApp
import edu.gatech.cc.cellwatch.core.util.PermissionManager
import edu.gatech.cc.cellwatch.data.model.LatencyData
import edu.gatech.cc.cellwatch.data.model.Location
import edu.gatech.cc.cellwatch.data.model.Measurement
import edu.gatech.cc.cellwatch.data.model.UploadDownloadData
import edu.gatech.cc.cellwatch.domain.telephony.managers.TelephonyInfoManager
import github.nisrulz.easydeviceinfo.base.EasyAppMod
import github.nisrulz.easydeviceinfo.base.EasyDeviceMod
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

abstract class MeasurementTest<T: Any>(val groupId: String, val type: String) {
    private val TAG = this::class.simpleName

    init {
        if (type !in listOf("upload", "download", "latency")) {
            throw Exception("unimplemented measurement type: $type")
        }
    }

    suspend fun run(): Measurement {
        val beginLocation = getLocation()
        val connectionType = TelephonyInfoManager.getConnectionType()
        val cellularDataEnabled = TelephonyInfoManager.isCellularDataEnabled()
        val cells = mutableListOf(*(TelephonyInfoManager.getCells()?.toTypedArray() ?: arrayOf()))
        val generations = mutableListOf(TelephonyInfoManager.getActiveNetworkGeneration())
        val stopWatching = TelephonyInfoManager.watchCells {
            cells.addAll(it)
            generations.add(TelephonyInfoManager.getActiveNetworkGeneration())
        }
        val result = try { measure() } finally { stopWatching() }
        val endLocation = getLocation()

        if (type == "latency" && result !is LatencyResult) {
            throw Exception("expected ${LatencyResult::class.simpleName} result, got ${result::class.simpleName}")
        }

        if ((type == "upload" || type == "download") && result !is ThroughputResult) {
            throw Exception("expected ${ThroughputResult::class.simpleName} result, got ${result::class.simpleName}")
        }

        val context = CellWatchApp.applicationContext()
        val deviceMod = EasyDeviceMod(context)
        val appMod = EasyAppMod(context)

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
            deviceId = CellWatchApp.settingsRepository.getDeviceId(),
            deviceManufacturer = deviceMod.manufacturer,
            deviceModel = Build.MODEL,
            deviceOsName = "Android",
            deviceOsVersion = deviceMod.osVersion,
            appName = appMod.appName,
            appVersion = BuildConfig.VERSION_NAME,
            provider = TelephonyInfoManager.getProviderName(),
            type = type,
            scheduled = false,
            cells = cells,
            locations = listOfNotNull(beginLocation, endLocation),
            simMcc = TelephonyInfoManager.getSimMobileCountryCode(),
            simMnc = TelephonyInfoManager.getSimMobileNetworkCode(),
            netMcc = TelephonyInfoManager.getNetworkMobileCountryCode(),
            netMnc = TelephonyInfoManager.getNetworkMobileNetworkCode(),
            carrierAggregation = TelephonyInfoManager.isUsingCarrierAggregation(cells),
            networkAvailable = TelephonyInfoManager.isNetworkAvailable(),
            networkConnected = TelephonyInfoManager.isNetworkConnected(),
            networkRoaming = TelephonyInfoManager.isNetworkRoaming(),
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

    private suspend fun getLocation(): Location? = suspendCoroutine { continuation ->
        val context = CellWatchApp.applicationContext()

        if (PermissionManager.checkLocationPermission()) {
            val client = LocationServices.getFusedLocationProviderClient(context)
            client.getCurrentLocation(
                CurrentLocationRequest.Builder()
                    .setGranularity(Granularity.GRANULARITY_FINE)
                    .setMaxUpdateAgeMillis(3000)
                    .build(),
                null
            ).addOnSuccessListener {
                continuation.resume(Location.fromAndroidLocation(it))
            }.addOnFailureListener {
                continuation.resumeWithException(it)
            }
        } else {
            continuation.resumeWithException(Exception("no location permission"))
        }
    }
}