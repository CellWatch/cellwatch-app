package com.example.ndt8.domain.ndt8.managers

import android.util.Log
import com.birjuvachhani.locus.Locus
import com.example.ndt8.CellWatchApp
import com.example.ndt8.data.datastore.LocalDataStore
import com.example.ndt8.data.local.model.LocationEntity
import com.example.ndt8.data.model.Location
import com.example.ndt8.data.model.Measurement
import com.example.ndt8.data.model.UploadDownloadData
import com.example.ndt8.domain.ndt8.model.Ndt8LocateServer
import com.example.ndt8.domain.ndt8.model.Ndt8TestDirection
import com.example.ndt8.domain.ndt8.model.Ndt8TestResult
import com.example.ndt8.domain.ndt8.services.Ndt8TestComponent
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
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

object Ndt8MeasurementManager {
    private var _bytesPerSecState = MutableStateFlow(0.0)
    val bytesPerSecState: StateFlow<Double> = _bytesPerSecState //.asStateFlow()

    private val measurementRepository = CellWatchApp.measurementRepository;
    private val TAG = this::class.simpleName
//    private var locationEntities: List<LocationEntity>? = null

    private var deviceMod: EasyDeviceMod? = null
    private var networkMod: EasyNetworkMod? = null
    private var simMod: EasySimMod? = null
    private var appMod: EasyAppMod? = null

    fun updateBytesPerSec(newBytesPerSec: Double) {
//        _bytesPerSecState.value = newBytesPerSec
        _bytesPerSecState.update { newBytesPerSec }
    }

    suspend fun runTestSequence() {
        val measurementId: String? = null
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

        writeMessage("network available = ${networkMod?.isNetworkAvailable.toString()}")
        writeMessage("wifi state = ${networkMod?.isWifiEnabled}")

        writeMessage("-----------------")

        writeMessage("Carrier = ${simMod?.carrier}")
        writeMessage("Country = ${simMod?.country}")

        writeMessage("-----------------")

        val client = OkHttpClient.Builder().build()

        writeMessage("selecting server")

        // use real M-Lab server
        val server = Ndt8LocateManager.selectServerAsync(client) //, "https://locate-dot-mlab-staging.appspot.com/v2/nearest/")

        writeMessage("selected server ${server.machine} in ${server.location}")
        runTest(client, server, measurementId, Ndt8TestDirection.DOWNLOAD)
        runTest(client, server, measurementId, Ndt8TestDirection.UPLOAD)

        // Try to upload measurements to Supabase
        measurementRepository.uploadMeasurements()
//        runBlocking { measurementRepository?.uploadMeasurementsWithData() }
    }

    suspend fun runTest(
        client: OkHttpClient,
        server: Ndt8LocateServer,
        measurementId: String?,
        direction: Ndt8TestDirection
    ) {
        var location: Location?
        val locations = ArrayList<Location>()

        val dir = if (direction == Ndt8TestDirection.DOWNLOAD) "download" else "upload"
        writeMessage("running $dir test")

        val result = try {
            // get test start GPS location
            location = getLocation()
            if (location != null)
                locations.add(location)
            else
                Log.e(TAG, "Error getting GPS location!!!")

            val test = Ndt8TestComponent(client, server, measurementId, direction)
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

        insertMeasurement(result, direction, locations)
//        runBlocking { createMeasurement(result, direction) }
    }

    suspend fun insertMeasurement(result: Ndt8TestResult, direction: Ndt8TestDirection, locations: List<Location>?) {
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
            deviceId = deviceId,
            deviceManufacturer = deviceMod?.manufacturer,
            deviceModel = deviceMod?.model,
            deviceOsName = "Android",
            deviceOsVersion = deviceMod?.osVersion,
            appName = appMod?.appName,
            provider = simMod?.carrier,
            type = direction.toString().lowercase(),
            duration = totalDuration,
            scheduled = false,
            success = result.success,
            carrierAggregation = false,
            networkAvailable = true,
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
