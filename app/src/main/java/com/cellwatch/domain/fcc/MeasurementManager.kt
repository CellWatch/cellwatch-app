package com.cellwatch.domain.fcc

import android.util.Log
import com.birjuvachhani.locus.Locus
import com.cellwatch.BuildConfig
import com.cellwatch.CellWatchApp
import com.cellwatch.data.datastore.LocalDataStore
import com.cellwatch.data.model.Cell
import com.cellwatch.data.model.LatencyData
import com.cellwatch.data.model.Location
import com.cellwatch.data.model.Measurement
import com.cellwatch.data.model.UploadDownloadData
import com.cellwatch.domain.msak.Server
import com.cellwatch.domain.msak.locate.LocateManager
import com.cellwatch.domain.msak.throughput.ThroughputDirection
import com.cellwatch.domain.telephony.managers.TelephonyInfoManager
import github.nisrulz.easydeviceinfo.base.EasyAppMod
import github.nisrulz.easydeviceinfo.base.EasyDeviceMod
import github.nisrulz.easydeviceinfo.base.EasySimMod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.security.InvalidParameterException
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine


object MeasurementManager {
    private var _bytesPerSecState = MutableStateFlow(0.0)
    val bytesPerSecState: StateFlow<Double> = _bytesPerSecState

    private val measurementRepository = com.cellwatch.CellWatchApp.measurementRepository
    private val TAG = this::class.simpleName

//    private lateinit var telephonyInfoManager: TelephonyInfoManager

    private var deviceMod: EasyDeviceMod? = EasyDeviceMod(CellWatchApp.applicationContext())
    private var simMod: EasySimMod? = EasySimMod(CellWatchApp.applicationContext())
    private var appMod: EasyAppMod? = EasyAppMod(CellWatchApp.applicationContext())

    fun updateBytesPerSec(newBytesPerSec: Double) {
        _bytesPerSecState.update { newBytesPerSec }
    }

    suspend fun runTestSequence(
        onLocateStart: () -> Unit,
        onLocateComplete: (r: String) -> Unit,
        onLatencyStart: () -> Unit,
        onLatencyComplete: (r: LatencyResult, l: List<Location>, c: List<Cell>) -> Unit,
        onDownloadStart: () -> Unit,
        onDownloadComplete: (r: ThroughputResult, l: List<Location>, c: List<Cell>) -> Unit,
        onUploadStart: () -> Unit,
        onUploadComplete: (r: ThroughputResult, l: List<Location>, c: List<Cell>) -> Unit,
    ) {
        val measurementId: String? = if (BuildConfig.MSAK_SERVER_ENV == "local") {
            UUID.randomUUID().toString()
        } else {
            null
        }

        Log.i(TAG,"RUNNING TEST SEQUENCE with measurement id $measurementId")

        val groupId: String = UUID.randomUUID().toString()
        val client = OkHttpClient.Builder().build()

        Log.i(TAG, "selecting server")

        onLocateStart()
        val servers = chooseMsakServers(client)
        val throughputServer = servers.first
        val latencyServer = servers.second
        onLocateComplete(throughputServer.machine)

        Log.i(TAG, "selected servers $throughputServer $latencyServer")

        onLatencyStart()
        val (latencyResult, latencyLocations, latencyCells) = runFullTest { runLatencyTest(client, latencyServer, measurementId) }
        onLatencyComplete(latencyResult, latencyLocations, latencyCells)
        insertMeasurement(groupId, latencyCells, latencyLocations, "latency", null, latencyResult)

        onDownloadStart()
        val (downloadResult, downloadLocations, downloadCells) = runFullTest { runThroughputTest(throughputServer, measurementId, ThroughputDirection.DOWNLOAD) }
        onDownloadComplete(downloadResult, downloadLocations, downloadCells)
        insertMeasurement(groupId, downloadCells, downloadLocations, "download", downloadResult, null)

        onUploadStart()
        val (uploadResult, uploadLocations, uploadCells) = runFullTest { runThroughputTest(throughputServer, measurementId, ThroughputDirection.UPLOAD) }
        onUploadComplete(uploadResult, uploadLocations, uploadCells)
        insertMeasurement(groupId, uploadCells, uploadLocations, "upload", uploadResult, null)

        measurementRepository.uploadMeasurements()
    }

    suspend fun <T> runFullTest(testFn: suspend () -> T): Triple<T, List<Location>, List<Cell>> {
        val beginLocation: Location? = getLocation()
        val cells = TelephonyInfoManager.getCells()
        val testResult = testFn()
        val endLocation: Location? = getLocation()

        return Triple(testResult, listOfNotNull(beginLocation, endLocation), cells ?: listOf())
    }

    suspend fun runThroughputTest(
        server: Server,
        measurementId: String?,
        direction: ThroughputDirection,
    ): ThroughputResult {
        val dir = if (direction == ThroughputDirection.DOWNLOAD) "download" else "upload"
        Log.i(TAG, "running $dir test")

        val throughputTestResult = try {
            val test = ThroughputTest(server, 3, direction, measurementId)

            coroutineScope {
                launch {
                    withContext(Dispatchers.IO) {
                        test.metricsChan.consumeEach {
                            Log.v(TAG, "got metrics $it")
                            updateBytesPerSec(it.bytesPerSec)
                        }
                    }
                }

                test.run()
            }
        } catch (t: Throwable) {
            Log.d(TAG, "$dir test failed", t)
            Log.e(TAG, "$dir test failed: ${t.localizedMessage}")
            throw t
        } finally {
            updateBytesPerSec(0.0)
            Log.d(TAG, "Done running test...")
        }

        Log.i(TAG, "$dir test complete: measurementId = $measurementId")
        Log.i(TAG, "$dir test complete: $throughputTestResult")

        return throughputTestResult
    }

    suspend fun runLatencyTest(
        client: OkHttpClient,
        server: Server,
        measurementId: String?,
    ): LatencyResult {
        val latencyResult = try {
            val test = LatencyTest(server, client, measurementId)

            coroutineScope {
                launch {
                    test.rttChan.consumeEach {
                        Log.v(TAG, "got rtt $it")
                    }
                }

                test.run()
            }
        } catch (t: Throwable) {
            Log.e(TAG, "latency test failed", t)
            throw t
        }

        Log.d(TAG, "got latency result: $latencyResult")
        return latencyResult
    }

    suspend fun insertMeasurement(
        groupId: String,
        cells: List<Cell>,
        locations: List<Location>,
        type: String,
        throughputResult: ThroughputResult?,
        latencyResult: LatencyResult?,
    ) {
        if (throughputResult == null && latencyResult == null) {
            throw InvalidParameterException("either throughput or latency result must be provided")
        }

        val context = CellWatchApp.applicationContext()
        val dataStore = LocalDataStore(context)
        val deviceId = dataStore.getDeviceId.first()

        val duration = if (throughputResult != null) {
            (throughputResult.activeMetrics?.usecs ?: 0) + (throughputResult.warmupMetrics?.usecs ?: 0)
        } else {
            latencyResult?.usecs
        }

        val latencyData = if (latencyResult != null) {
            LatencyData(
                rtt = latencyResult.meanRtt,
                jitter = latencyResult.jitter,
                sent = latencyResult.packetsSent,
                received = latencyResult.packetsReceived,
                servers = listOf(latencyResult.targetHost),
            )
        } else {
            null
        }

        val uploadDownloadData = if (throughputResult != null) {
            UploadDownloadData(
                warmupDuration = throughputResult.warmupMetrics?.usecs,
                warmupBytes = throughputResult.warmupMetrics?.bytes,
                duration = throughputResult.activeMetrics?.usecs,
                bytes = throughputResult.activeMetrics?.bytes,
                servers = listOf(throughputResult.targetHost),
            )
        } else {
            null
        }

        val (simMcc, simMnc) = TelephonyInfoManager.getSimMobileCodes()
        val (netMcc, netMnc) = TelephonyInfoManager.getNetMobileCodes()

        val measurement = Measurement(
            groupId = groupId,
            deviceId = deviceId,
            deviceManufacturer = deviceMod?.manufacturer,
            deviceModel = deviceMod?.model,
            deviceOsName = "Android",
            deviceOsVersion = deviceMod?.osVersion,
            appName = appMod?.appName,
            provider = simMod?.carrier,
            type = type,
            timestamp = throughputResult?.start ?: latencyResult?.start,
            duration = duration,
            scheduled = false,
            success = throughputResult?.success ?: latencyResult?.success,
            carrierAggregation = false, // TODO: how do we get this?
            networkAvailable = TelephonyInfoManager.isNetworkAvailable(),
            networkConnected = TelephonyInfoManager.isNetworkConnected(),
            networkRoaming = TelephonyInfoManager.isNetworkRoaming(),
            uploadDownloadData = uploadDownloadData,
            latencyData = latencyData,
            cells = cells,
            locations = locations,
            simMcc = simMcc,
            simMnc = simMnc,
            netMcc = netMcc,
            netMnc = netMnc,
        )

        Log.d(TAG, "Insert ${measurement.type} measurement with id = ${measurement.id}")

        try {
            measurementRepository.insertMeasurement(measurement)
        } catch (e: Exception) {
            Log.e(TAG, "Error inserting new measurement in MeasurementManager: ${e.message}")
            throw e
        }
    }

    suspend fun getLocation(): Location? = suspendCoroutine { continuation ->
        val context = CellWatchApp.applicationContext()
        var location: Location? = null //android.location.Location? = null

        Locus.getCurrentLocation(context) { locationResult ->
            locationResult.location?.let { /* Received location update */
                location = Location.fromAndroidLocation(locationResult.location)
                Log.d(TAG,"lat/lon: ${location?.lat} / ${location?.lon}")
                Log.d(TAG,"accuracy: ${location?.accuracy}")
                Log.d(TAG,"heading: ${location?.heading}")
            }
            locationResult.error?.let { /* Received error! */
                Log.e(TAG,"Got a location services error!!! ${it.message}")
            }
            continuation.resume(location)
        }
    }

    suspend fun chooseMsakServers(client: OkHttpClient? = null): Pair<Server, Server> {
        val manager = LocateManager(client)
        val throughputServers = manager.locateThroughputServers()

        if (throughputServers.isEmpty()) {
            Log.e(TAG, "no throughput servers")
            throw Throwable("no throughput servers found")
        }

        val throughputServer =  try {
            throughputServers.maxBy { ping(it.machine) }
        } catch (t: Throwable) {
            Log.e(TAG, "pinging available servers failed", t)
            throughputServers[0]
        }

        val latencyServers = manager.locateLatencyServers(throughputServer)

        if (latencyServers.isEmpty()) {
            Log.e(TAG, "no latency servers at matching throughput server ${throughputServer.machine}")
            throw Throwable("no latency servers found")
        }

        return Pair(throughputServer, latencyServers[0])
    }
}
