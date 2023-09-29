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
import github.nisrulz.easydeviceinfo.base.EasyNetworkMod
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
import kotlinx.datetime.Clock
import okhttp3.OkHttpClient
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine


object MeasurementManager {
    private var _bytesPerSecState = MutableStateFlow(0.0)
    val bytesPerSecState: StateFlow<Double> = _bytesPerSecState

    private val measurementRepository = com.cellwatch.CellWatchApp.measurementRepository
    private val TAG = this::class.simpleName

    private var deviceMod: EasyDeviceMod? = EasyDeviceMod(CellWatchApp.applicationContext())
    private var networkMod: EasyNetworkMod = EasyNetworkMod(CellWatchApp.applicationContext())
    private var simMod: EasySimMod? = EasySimMod(CellWatchApp.applicationContext())
    private var appMod: EasyAppMod? = EasyAppMod(CellWatchApp.applicationContext())

    fun updateBytesPerSec(newBytesPerSec: Double) {
        _bytesPerSecState.update { newBytesPerSec }
    }

    suspend fun runTestSequence(
        onLocateStart: () -> Unit,
        onLocateComplete: (r: String) -> Unit,
        onLatencyStart: () -> Unit,
        onLatencyComplete: (r: FullLatencyResult) -> Unit,
        onDownloadStart: () -> Unit,
        onDownloadComplete: (r: FullThroughputResult) -> Unit,
        onUploadStart: () -> Unit,
        onUploadComplete: (r: FullThroughputResult) -> Unit,
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
        val fullLatencyResult = runFullLatencyTest(client, latencyServer, measurementId)
        onLatencyComplete(fullLatencyResult)
        insertLatency(groupId, fullLatencyResult.latencyResult, fullLatencyResult.cells, fullLatencyResult.locations)

        onDownloadStart()
        val downloadResult = runFullThroughputTest(throughputServer, measurementId, ThroughputDirection.DOWNLOAD)
        onDownloadComplete(downloadResult)
        insertMeasurement(groupId, downloadResult.throughputTestResult, ThroughputDirection.DOWNLOAD, downloadResult.cells, downloadResult.locations)

        onUploadStart()
        val uploadResult = runFullThroughputTest(throughputServer, measurementId, ThroughputDirection.UPLOAD)
        onUploadComplete(uploadResult)
        insertMeasurement(groupId, uploadResult.throughputTestResult, ThroughputDirection.UPLOAD, uploadResult.cells, uploadResult.locations)

        measurementRepository.uploadMeasurements()
    }

    suspend fun runFullLatencyTest(
        client: OkHttpClient,
        server: Server,
        measurementId: String?
    ): FullLatencyResult {
        val beginLocation: Location? = getLocation()
        val cells = TelephonyInfoManager.getCells()
        val latencyResult = runLatencyTest(client, server, measurementId)
        val endLocation: Location? = getLocation()

        return FullLatencyResult(
            latencyResult,
            listOfNotNull(beginLocation, endLocation),
            cells
        )
    }

    suspend fun runFullThroughputTest(
        server: Server,
        measurementId: String?,
        direction: ThroughputDirection,
    ): FullThroughputResult {
        val beginLocation: Location? = getLocation()
        val cells = TelephonyInfoManager.getCells()
        val throughputTestResult = runThroughputTest(server, measurementId, direction)
        val endLocation: Location? = getLocation()

        return FullThroughputResult(
            throughputTestResult,
            listOfNotNull(beginLocation, endLocation),
            cells
        )
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

    suspend fun insertLatency(
        groupId: String,
        latencyResult: LatencyResult,
        cells: List<Cell>?,
        locations: List<Location>?
    ) {
        val context = CellWatchApp.applicationContext()
        val dataStore = LocalDataStore(context)
        val deviceId = dataStore.getDeviceId.first()
        val servers: List<String> = listOf(latencyResult.targetHost)

        val latencyData = LatencyData(
            rtt = latencyResult.meanRtt,
            jitter = latencyResult.jitter,
            sent = latencyResult.packetsSent,
            received = latencyResult.packetsReceived,
            servers = servers
        )

        val measurement = Measurement(
            groupId = groupId,
            deviceId = deviceId,
            deviceManufacturer = deviceMod?.manufacturer,
            deviceModel = deviceMod?.model,
            deviceOsName = "Android",
            deviceOsVersion = deviceMod?.osVersion,
            appName = appMod?.appName,
            provider = simMod?.carrier,
            type = "latency",
            timestamp = Clock.System.now(),
            duration = null,
            scheduled = false,
            success = latencyResult.success,
            carrierAggregation = null,
            networkAvailable = networkMod.isNetworkAvailable,
            networkConnected = networkMod.isNetworkAvailable,
            networkRoaming = false,
            uploadDownloadData = null,
            latencyData = latencyData,
            cells = cells,
            locations = locations
        )
        Log.d(TAG, "Insert ${measurement.type} measurement with id = ${measurement.id}")

        try {
            measurementRepository.insertMeasurement(measurement)
        } catch (e: Exception) {
            Log.e(TAG, "Error inserting new measurement in MeasurementManager: ${e.message}")
            throw e
        }
    }

    suspend fun insertMeasurement(
        groupId: String,
        throughputTestResult: ThroughputResult,
        direction: ThroughputDirection,
        cells: List<Cell>?,
        locations: List<Location>?
    ) {
        val context = CellWatchApp.applicationContext()
        val dataStore = LocalDataStore(context)
        val deviceId = dataStore.getDeviceId.first()

        val warmupDuration = if (throughputTestResult.warmupMetrics != null)
            throughputTestResult.warmupMetrics.usecs else 0

        val activeDuration = if (throughputTestResult.activeMetrics != null)
            throughputTestResult.activeMetrics.usecs else 0

        val totalDuration = warmupDuration + activeDuration

        val server = throughputTestResult.targetHost

        // TODO: fix this -- I don't understand why it's needed
        //var client = if (result.streamResults.isNotEmpty())
        //    result.streamResults.first()?.localAddr else "client"
        //if (client == null) client = "none"
        var client = "client"

        val servers = listOf(client, server)

        val uploadDownloadData = UploadDownloadData(
            warmupDuration = throughputTestResult.warmupMetrics?.usecs,
            warmupBytes = throughputTestResult.warmupMetrics?.bytes,
            duration = throughputTestResult.activeMetrics?.usecs,
            bytes = throughputTestResult.activeMetrics?.bytes,
            servers = servers
        )

        val measurement = Measurement(
            groupId = groupId,
            deviceId = deviceId,
            deviceManufacturer = deviceMod?.manufacturer,
            deviceModel = deviceMod?.model,
            deviceOsName = "Android",
            deviceOsVersion = deviceMod?.osVersion,
            appName = appMod?.appName,
            provider = simMod?.carrier,
            type = direction.toString().lowercase(),
            timestamp = Clock.System.now(),
            duration = totalDuration,
            scheduled = false,
            success = throughputTestResult.success,
            carrierAggregation = false,
            networkAvailable = networkMod.isNetworkAvailable,
            networkConnected = true,
            networkRoaming = false,
            uploadDownloadData = uploadDownloadData,
            cells = cells,
            locations = locations
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
