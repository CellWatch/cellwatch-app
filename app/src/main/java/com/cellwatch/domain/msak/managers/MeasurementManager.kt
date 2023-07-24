package com.cellwatch.domain.msak.managers

import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.annotation.RequiresApi
import com.birjuvachhani.locus.Locus
import com.cellwatch.CellWatchApp
import com.cellwatch.data.datastore.LocalDataStore
import com.cellwatch.data.model.Location
import com.cellwatch.data.model.Measurement
import com.cellwatch.data.model.UploadDownloadData
import com.cellwatch.domain.msak.model.LocateServer
import com.cellwatch.domain.msak.model.MsakTestDirection
import com.cellwatch.domain.msak.model.ThroughputTestResult
import com.cellwatch.domain.msak.services.LatencyTest
import com.cellwatch.domain.msak.services.ThroughputTestComponent
import com.cellwatch.domain.telephony.managers.TelephonyInfoManager
import github.nisrulz.easydeviceinfo.base.EasyAppMod
import github.nisrulz.easydeviceinfo.base.EasyDeviceMod
import github.nisrulz.easydeviceinfo.base.EasyNetworkMod
import github.nisrulz.easydeviceinfo.base.EasySimMod
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import okhttp3.OkHttpClient
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine


object MeasurementManager {
    private var _bytesPerSecState = MutableStateFlow(0.0)
    val bytesPerSecState: StateFlow<Double> = _bytesPerSecState //.asStateFlow()

    private val measurementRepository = com.cellwatch.CellWatchApp.measurementRepository;
    private val TAG = this::class.simpleName
//    private var locationEntities: List<LocationEntity>? = null

    private lateinit var telephonyInfoManager: TelephonyInfoManager

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
        val useLocalServer = false
        val measurementId: String? = if (useLocalServer) UUID.randomUUID().toString() else null
        val groupId: String = UUID.randomUUID().toString()

        writeMessage("RUNNING TEST SEQUENCE with measurement id $measurementId")

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

        val server = if (useLocalServer) {
            writeMessage("Using local server")
            LocateServer(
                "10.0.2.2", null, mapOf(
                    "ws:///throughput/v1/download" to "ws://10.0.2.2:8080/throughput/v1/download",
                    "ws:///throughput/v1/upload" to "ws://10.0.2.2:8080/throughput/v1/upload",
                )
            )
        } else {
            writeMessage("Using MLabs server")
            LocateManager.selectServerAsync(client)
        }

        writeMessage("selected server ${server.machine} in ${server.location}")
//        runLatencyTest(client, server, measurementId)
        runThroughputTest(client, server, measurementId, groupId, MsakTestDirection.DOWNLOAD)
        runThroughputTest(client, server, measurementId, groupId, MsakTestDirection.UPLOAD)

        // Try to upload measurements to Supabase
        measurementRepository.uploadMeasurements()
//        runBlocking { measurementRepository?.uploadMeasurementsWithData() }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    suspend fun runThroughputTest(
        client: OkHttpClient,
        server: LocateServer,
        measurementId: String?,
        groupId: String,
        direction: MsakTestDirection
    ) {
        var location: Location?
        val locations = ArrayList<Location>()

        val dir = if (direction == MsakTestDirection.DOWNLOAD) "download" else "upload"
        writeMessage("running $dir test")

        val result = try {
            // get test start GPS location
            location = getLocation()
            if (location != null)
                locations.add(location)
            else
                Log.e(TAG, "Error getting GPS location!!!")

            val test = ThroughputTestComponent(client, server, measurementId, direction)
            // Get device connection info
//            Log.d(TAG, "Starting getCellInfo test *******")
//            val cells = telephonyInfoManager.getCells()
//            cells?.forEach { cell ->
//                Log.d(TAG, "${cell.toString()}")
//            }

//            runBlocking {
            coroutineScope {
                launch {
                    test.progress.consumeEach() {
                        Log.d(TAG, "got progress $it")
//                        writeMessage("progress: $it")
                        updateBytesPerSec(it.bytesPerSec)
//                        writeMessage("***** speed = ${8 * it.bytesPerSec / 1e6}")
                    }
                }

                test.run()
            }
        } catch (t: Throwable) {
            Log.d(TAG, "$dir test failed", t)
            writeMessage("$dir test failed: ${t.localizedMessage}")
            return
        } finally {
            updateBytesPerSec(0.0)
            Log.d(TAG, "Done running test...")
            // get test end GPS location
            // get test start GPS location
            location = getLocation()
            if (location != null)
                locations.add(location)
        }

        Log.i(TAG, "$dir test complete: measurementId = $measurementId")
        Log.i(TAG, "$dir test complete: $result")
        Log.i(TAG, "$dir test locations = ${locations.map { it.id }.joinToString()}")
//        writeMessage("$dir test complete: ${if (result.success) "success" else "failure"}; warmup ${result.warmupMetrics}; active ${result.activeMetrics}")

        insertMeasurement(groupId, result, direction, locations)
//        runBlocking { createMeasurement(result, direction) }
    }

    suspend fun runLatencyTest(
        client: OkHttpClient,
        server: LocateServer,
        measurementId: String?,
    ) {
        val result = try {
            val test = LatencyTest(client, server, measurementId)

            coroutineScope {
                launch {
                    test.progress.consumeEach {
                        Log.d(TAG, "got progress $it")
                    }
                }

                test.run()
            }
        } catch (t: Throwable) {
            Log.e(TAG, "latency test failed", t)
        }

        Log.d(TAG, "got latency result: $result")
    }

    suspend fun insertMeasurement(groupId: String, result: ThroughputTestResult, direction: MsakTestDirection, locations: List<Location>?) {
        val context = CellWatchApp.applicationContext()
        val dataStore = LocalDataStore(context)
        var deviceId = dataStore.getDeviceId.first()
//        var deviceId = runBlocking {
//            dataStore.getDeviceId.first()
//        }

        val warmupDuration = if (result.warmupMetrics != null)
            result.warmupMetrics.usecs else 0

        val activeDuration = if (result.activeMetrics != null)
            result.activeMetrics.usecs else 0

        val totalDuration = warmupDuration + activeDuration

        var server = if (result.streamResults.isNotEmpty())
            result.streamResults.first()?.remoteAddr else "server"

        var client = if (result.streamResults.isNotEmpty())
            result.streamResults.first()?.localAddr else "client"

        if (server == null) server = "none"

        if (client == null) client = "none"

        val servers = listOf(client, server)
//        val serversString = servers.joinToString(prefix = "{", postfix = "}", separator = ",")

        val uploadDownloadData = UploadDownloadData(
//            measurementId = measurement.id,
            warmupDuration = result.warmupMetrics?.usecs,
            warmupBytes = result.warmupMetrics?.bytes,
            duration = result.activeMetrics?.usecs,
            bytes = result.activeMetrics?.bytes,
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
            success = result.success,
            carrierAggregation = false,
            networkAvailable = networkMod.isNetworkAvailable,
            networkConnected = true,
            networkRoaming = false,
            uploadDownloadData = uploadDownloadData,
            locations = locations
        )
        Log.d(TAG,"measurement = $measurement")

//        measurement.uploadDownloadData?.measurementId = measurement.id

//        val uploadDownloadData = UploadDownloadData(
//            measurementId = measurement.id,
//            warmupDuration = result.warmupMetrics?.usecs,
//            warmupBytes = result.warmupMetrics?.bytes,
//            duration = result.activeMetrics?.usecs,
//            bytes = result.activeMetrics?.bytes,
//            servers = servers
//        )

//        measurement.uploadDownloadData = uploadDownloadData

//        var location: android.location.Location? = null
//
//        Locus.getCurrentLocation(context) { locationResult ->
//            locationResult.location?.let { /* Received location update */
//                location = locationResult.location
//                Log.d(TAG,"lat/lon: ${location?.latitude} / ${location?.longitude}")
//                Log.d(TAG,"accuracy: ${location?.accuracy}")
//                Log.d(TAG,"heading: ${location?.bearing}")
//            }
//            locationResult.error?.let { /* Received error! */
//                Log.e(TAG,"Got a location services error!!! ${it.message}")
//            }
//        }

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

    fun writeMessage(m: String) {
        Log.i(TAG, m)
    }
}
