package com.cellwatch.domain.fcc

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
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
    val bytesPerSecState: StateFlow<Double> = _bytesPerSecState //.asStateFlow()

    private val measurementRepository = com.cellwatch.CellWatchApp.measurementRepository
    private val TAG = this::class.simpleName
//    private var locationEntities: List<LocationEntity>? = null

    private var deviceMod: EasyDeviceMod? = EasyDeviceMod(CellWatchApp.applicationContext())
    private var networkMod: EasyNetworkMod = EasyNetworkMod(CellWatchApp.applicationContext())
    private var simMod: EasySimMod? = EasySimMod(CellWatchApp.applicationContext())
    private var appMod: EasyAppMod? = EasyAppMod(CellWatchApp.applicationContext())

    fun updateBytesPerSec(newBytesPerSec: Double) {
//        _bytesPerSecState.value = newBytesPerSec
        _bytesPerSecState.update { newBytesPerSec }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    suspend fun runTestSequence() {
        val measurementId: String? = if (BuildConfig.MSAK_SERVER_ENV == "local") {
            UUID.randomUUID().toString()
        } else {
            null
        }

        val groupId: String = UUID.randomUUID().toString()

        writeMessage("RUNNING TEST SEQUENCE with measurement id $measurementId")

        // Get device connection info
//        Log.d(TAG, "Starting getCellInfo test *******")
//        var cells = TelephonyInfoManager.getCells()
//        Log.d(TAG, "Got ${cells?.size} cells")
//        cells?.forEach { cell ->
//            Log.d(TAG, "cell: ${cell.toString()}")
//        }

        writeMessage("-----------------")
        writeMessage("Device Manufacturer = ${deviceMod?.manufacturer}")
        writeMessage("Device Model = ${deviceMod?.model}")
        writeMessage("Device = ${deviceMod?.device}")
        writeMessage("OS Version = ${deviceMod?.osVersion}")
        deviceMod?.let { writeMessage(it.board) }
        deviceMod?.let { writeMessage(it.buildBrand) }
        deviceMod?.let { writeMessage(it.buildHost) }
        deviceMod?.let { writeMessage(it.buildID) }
        deviceMod?.let { writeMessage(it.buildVersionCodename) }
        deviceMod?.let { writeMessage(it.displayVersion) }
        deviceMod?.let { writeMessage(it.fingerprint) }

        writeMessage("-----------------")

//        val appMod = EasyAppMod(context)
        appMod?.let { writeMessage(it.appName) }
        appMod?.let { writeMessage(it.appVersion) }
        appMod?.let { writeMessage(it.appVersionCode) }
        appMod?.let { writeMessage(it.activityName) }

        writeMessage("-----------------")

        writeMessage("network available = ${networkMod.isNetworkAvailable.toString()}")
        writeMessage("wifi state = ${networkMod.isWifiEnabled}")
        
//        @NetworkType val networkType: Int = networkMod.getNetworkType()
//
//        when (networkType) {
//            NetworkType.CELLULAR_UNKNOWN -> writeMessage("Network Type : Unknown")
//            NetworkType.CELLULAR_UNIDENTIFIED_GEN -> writeMessage("Network Type : Cellular Unidentified Generation")
//            NetworkType.CELLULAR_2G -> writeMessage("Network Type : Cellular 2G")
//            NetworkType.CELLULAR_3G -> writeMessage("Network Type : Cellular 3G")
//            NetworkType.CELLULAR_4G -> writeMessage("Network Type : Cellular 4G")
//            NetworkType.WIFI_WIFIMAX -> writeMessage("Network Type : WIFI/WIFIMAX")
//            NetworkType.UNKNOWN -> writeMessage("Network Type : Unknown")
//            else -> writeMessage("Network Type : Unknown")
//        }
        writeMessage("-----------------")

        writeMessage("Carrier = ${simMod?.carrier}")
        writeMessage("Country = ${simMod?.country}")

        writeMessage("-----------------")

        val client = OkHttpClient.Builder().build()

        writeMessage("selecting server")

        val servers = chooseMsakServers(client)
        val throughputServer = servers.first
        val latencyServer = servers.second

        writeMessage("selected servers $throughputServer $latencyServer")

        val fullLatencyResult = runFullLatencyTest(client, latencyServer, measurementId)
        insertLatency(groupId, fullLatencyResult.latencyResult, fullLatencyResult.cells, fullLatencyResult.locations)

        val downloadResult = runFullThroughputTest(throughputServer, measurementId, ThroughputDirection.DOWNLOAD)
        insertMeasurement(groupId, downloadResult.throughputTestResult, ThroughputDirection.DOWNLOAD, downloadResult.cells, downloadResult.locations)

        val uploadResult = runFullThroughputTest(throughputServer, measurementId, ThroughputDirection.UPLOAD)
        insertMeasurement(groupId, uploadResult.throughputTestResult, ThroughputDirection.UPLOAD, uploadResult.cells, uploadResult.locations)

        // Try to upload measurements to Supabase
        measurementRepository.uploadMeasurements()
//        runBlocking { measurementRepository?.uploadMeasurementsWithData() }
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

//        if (throughputTestResult == null) return null

        val endLocation: Location? = getLocation()

        return FullThroughputResult(
            throughputTestResult,
            listOfNotNull(beginLocation, endLocation),
            cells
        )
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    suspend fun runThroughputTest(
        server: Server,
        measurementId: String?,
//        groupId: String,
        direction: ThroughputDirection,
    ): ThroughputResult {
//        var cells: List<Cell>?
//        var location: Location?
//        val locations = ArrayList<Location>()

        val dir = if (direction == ThroughputDirection.DOWNLOAD) "download" else "upload"
        writeMessage("running $dir test")

        val throughputTestResult = try {
//            cells = TelephonyInfoManager.getCells()

            // get test start GPS location
//            location = getLocation()
//            if (location != null)
//                locations.add(location)
//            else
//                Log.e(TAG, "Error getting GPS location!!!")

            val test = ThroughputTest(server, 3, direction, measurementId)

//            runBlocking {
            coroutineScope {
                launch {
                    withContext(Dispatchers.IO) {
                        test.metricsChan.consumeEach {
                            Log.v(TAG, "got metrics $it")
//                        writeMessage("progress: $it")
                            updateBytesPerSec(it.bytesPerSec)
//                        writeMessage("***** speed = ${8 * it.bytesPerSec / 1e6}")
                        }
                    }
                }

                test.run()
            }
        } catch (t: Throwable) {
            Log.d(TAG, "$dir test failed", t)
            writeMessage("$dir test failed: ${t.localizedMessage}")
            throw t
//            return ThroughputTestResult(success = false)
        } finally {
            updateBytesPerSec(0.0)
            Log.d(TAG, "Done running test...")
            // get test end GPS location
            // get test start GPS location
//            location = getLocation()
//            if (location != null)
//                locations.add(location)
        }

        Log.i(TAG, "$dir test complete: measurementId = $measurementId")
        Log.i(TAG, "$dir test complete: $throughputTestResult")
//        Log.i(TAG, "$dir test locations = ${locations.map { it.id }.joinToString()}")
//        writeMessage("$dir test complete: ${if (result.success) "success" else "failure"}; warmup ${result.warmupMetrics}; active ${result.activeMetrics}")

        return throughputTestResult

//        insertMeasurement(groupId, result, direction, cells, locations)
//        runBlocking { createMeasurement(result, direction) }
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
//            return LatencyResult(
//                serverHost,
//                false,
//                Clock.System.now(),
//                0,
//                0,
//                0,
//                0,
//                0,
//            )
        }

        Log.d(TAG, "got latency result: $latencyResult")
//        insertLatency(measurementId, result)
        return latencyResult
    }

//    suspend fun insertLatency(
//        measurementId: String?,
//        result: LatencyResult
//    ) {
//
//    }

    suspend fun insertLatency(
        groupId: String,
        latencyResult: LatencyResult,
        cells: List<Cell>?,
        locations: List<Location>?
    ) {
        val context = CellWatchApp.applicationContext()
        val dataStore = LocalDataStore(context)
        val deviceId = dataStore.getDeviceId.first()

        val servers: List<String>? = listOf(latencyResult.targetHost)
//        val servers: List<String>? = if (latencyResult.remoteAddr != null) listOf(latencyResult.remoteAddr) else null

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
//        val serversString = servers.joinToString(prefix = "{", postfix = "}", separator = ",")

        val uploadDownloadData = UploadDownloadData(
//            measurementId = measurement.id,
            warmupDuration = throughputTestResult.warmupMetrics?.usecs,
            warmupBytes = throughputTestResult.warmupMetrics?.bytes,
            duration = throughputTestResult.activeMetrics?.usecs,
            bytes = throughputTestResult.activeMetrics?.bytes,
            servers = servers
        )

//        Log.d(TAG, "insertMeasurement: cells = ${cells}")

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
//        val insertedMeasurement = measurementNetworkDatasource?.insertMeasurement(measurement)
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

    fun writeMessage(m: String) {
        Log.i(TAG, m)
    }
}
