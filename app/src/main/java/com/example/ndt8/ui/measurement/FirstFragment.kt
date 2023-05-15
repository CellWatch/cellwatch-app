package com.example.ndt8.ui.measurement

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.example.ndt8.CellWatchApp
import com.example.ndt8.R
import com.example.ndt8.databinding.FragmentFirstBinding
import com.example.ndt8.data.core.repositories.MeasurementRepository
import com.example.ndt8.domain.ndt8.managers.Ndt8MeasurementManager
import com.example.ndt8.ui.measurement.viewmodels.MeasurementViewModel
import com.example.ndt8.ui.measurement.viewmodels.MeasurementViewModelFactory
import com.github.anastr.speedviewlib.SpeedView
import github.nisrulz.easydeviceinfo.base.*
import kotlinx.coroutines.launch


/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class FirstFragment : Fragment() {
    private val TAG = this::class.simpleName
    private var _binding: FragmentFirstBinding? = null

    private val measurementViewModel: MeasurementViewModel by activityViewModels() {
        MeasurementViewModelFactory(CellWatchApp.measurementRepository)
    }

    private var deviceMod: EasyDeviceMod? = null
    private var networkMod: EasyNetworkMod? = null
    private var simMod: EasySimMod? = null
//    private var locationMod: EasyLocationMod? = null
    private var appMod: EasyAppMod? = null
    private lateinit var speedometer: SpeedView

    private var measurementRepository: MeasurementRepository? = null;
//    private var measurementNetworkRepository: MeasurementNetworkRepository? = null

//    private var _bytesPerSecState = MutableStateFlow(0.0)
//    val bytesPerSecState: StateFlow<Double> = _bytesPerSecState //.asStateFlow()

//    fun updateBytesPerSec(newBytesPerSec: Double) {
////        _bytesPerSecState.value = newBytesPerSec
//        _bytesPerSecState.update { newBytesPerSec }
//    }

//    private var measurementNetworkDatasource: MeasurementNetworkDatasource? = null;

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentFirstBinding.inflate(inflater, container, false)
        return binding.root

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        speedometer = binding.speedView
        speedometer.setMinMaxSpeed(0F, 100F)
        speedometer.withTremble = false
//        speedometer.speedTo(45F)

        binding.textviewFirst.movementMethod = ScrollingMovementMethod()

        binding.buttonFirst.setOnClickListener {
            toggleButton(false)

            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    Ndt8MeasurementManager.runTestSequence()
                } catch (e: Exception) {
                    Log.e(TAG, "unexpected error running test sequence", e)
                    writeMessage("unexpected error running test sequence: ${e.localizedMessage}")
                } finally {
                    writeMessage("*** Done with Upload/Download Test ***")
                }

                toggleButton(true)
            }
        }
//        binding.buttonFirst.setOnClickListener {
//            thread {
//                toggleButton(false)
//
//                try {
//                    runBlocking {
//                        Ndt8MeasurementManager.runTestSequence()
//                    }
//                } catch (e: Exception) {
//                    Log.e(TAG, "unexpected error running test sequence", e)
//                    writeMessage("unexpected error running test sequence: ${e.localizedMessage}")
//                } finally {
//                    writeMessage("*** Done with Upload/Download Test ***")
//                }
//
//                toggleButton(true)
//            }
//        }

        deviceMod = EasyDeviceMod(context)
        networkMod = EasyNetworkMod(context)
        simMod = EasySimMod(context)
//        locationMod = EasyLocationMod(context)
        appMod = EasyAppMod(context)

//        measurementRepository = CellWatchApp.measurementRepository
//        measurementNetworkRepository = CellWatchApp.measurementNetworkRepository

//        measurementNetworkDatasource = MeasurementNetworkDatasource.getInstance(context!!.applicationContext)

        viewLifecycleOwner.lifecycleScope.launch {
            measurementViewModel.bytesPerSecState.collect { bytesPerSec ->
                updateSpeedometer(8 * bytesPerSec / 1e6)
            }
        }
        Log.d(TAG, "***** FirstFragment Created *****")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }


//    private fun runTestSequence() {
//        val measurementId: String? = null
//        writeMessage("RUNNING TEST SEQUENCE with measurement id $measurementId")
//
//        writeMessage("-----------------")
//        writeMessage("Device Manufacturer = ${deviceMod?.manufacturer}")
//        writeMessage("Device Model = ${deviceMod?.model}")
//        writeMessage("Device = ${deviceMod?.device}")
//        writeMessage("OS Version = ${deviceMod?.osVersion}")
//        deviceMod?.let { writeMessage(it.board) }
//        deviceMod?.let { writeMessage(it.buildBrand) }
//        deviceMod?.let { writeMessage(it.buildHost) }
//        deviceMod?.let { writeMessage(it.buildID) }
//        deviceMod?.let { writeMessage(it.buildVersionCodename) }
//        deviceMod?.let { writeMessage(it.displayVersion) }
//        deviceMod?.let { writeMessage(it.fingerprint) }
//
//        writeMessage("-----------------")
//
////        val appMod = EasyAppMod(context)
//        appMod?.let { writeMessage(it.appName) }
//        appMod?.let { writeMessage(it.appVersion) }
//        appMod?.let { writeMessage(it.appVersionCode) }
//        appMod?.let { writeMessage(it.activityName) }
//
//        writeMessage("-----------------")
//
//        writeMessage("network available = ${networkMod?.isNetworkAvailable.toString()}")
//        writeMessage("wifi state = ${networkMod?.isWifiEnabled}")
//
//        writeMessage("-----------------")
//
//        writeMessage("Carrier = ${simMod?.carrier}")
//        writeMessage("Country = ${simMod?.country}")
//
//        writeMessage("-----------------")
//
//        val client = OkHttpClient.Builder().build()
//
//        writeMessage("selecting server")
//
//        // use real M-Lab server
//        val server = Ndt8LocateManager.selectServer(client) //, "https://locate-dot-mlab-staging.appspot.com/v2/nearest/")
//
//        writeMessage("selected server ${server.machine} in ${server.location}")
//        runTest(client, server, measurementId, Ndt8TestDirection.DOWNLOAD)
//        runTest(client, server, measurementId, Ndt8TestDirection.UPLOAD)
//
//        // Try to upload measurements to Supabase
//        runBlocking { measurementRepository?.uploadMeasurementsWithData() }
//    }

//    private fun runTest(
//        client: OkHttpClient,
//        server: Ndt8LocateServer,
//        measurementId: String?,
//        direction: Ndt8TestDirection
//    ) {
//        val dir = if (direction == Ndt8TestDirection.DOWNLOAD) "download" else "upload"
//        writeMessage("running $dir test")
//        val result = try {
//            val test = Ndt8TestComponent(client, server, measurementId, direction)
//            runBlocking {
//                launch {
//                    test.progress.consumeEach {
////                        Log.d(TAG, "got progress $it")
////                        writeMessage("progress: $it")
//
////                        writeMessage("***** speed = ${8 * it.bytesPerSec / 1e6}")
//                        updateBytesPerSec(it.bytesPerSec)
//
////                        updateSpeedometer(8 * it.bytesPerSec / 1e6)
//                    }
//                }
//
//                test.run()
//            }
//        } catch (t: Throwable) {
//            Log.d(TAG, "$dir test failed", t)
//            writeMessage("$dir test failed: ${t.localizedMessage}")
//            return
//        } finally {
//            updateSpeedometer(0.0, 1500)
//        }
//
//        Log.i(TAG, "$dir test complete: measurementId = $measurementId")
//        Log.i(TAG, "$dir test complete: $result")
//        writeMessage("$dir test complete: ${if (result.success) "success" else "failure"}; warmup ${result.warmupMetrics}; active ${result.activeMetrics}")
//
//        runBlocking { insertMeasurement(result, direction) }
////        runBlocking { createMeasurement(result, direction) }
//    }

//    suspend fun insertMeasurement(result: Ndt8TestResult, direction: Ndt8TestDirection) {
//        val dataStore = LocalDataStore(context!!)
//        var deviceId = runBlocking {
//            dataStore.getDeviceId.first()
//        }
//
//        val warmupDuration = if (result.warmupMetrics != null)
//            result.warmupMetrics.usecs else 0
//
//        val activeDuration = if (result.activeMetrics != null)
//            result.activeMetrics.usecs else 0
//
//        val totalDuration = warmupDuration + activeDuration
//
//        var server = if (result.streamResults.isNotEmpty())
//            result.streamResults.first()?.remoteAddr else "server"
//
//        var client = if (result.streamResults.isNotEmpty())
//            result.streamResults.first()?.localAddr else "client"
//
//        if (server == null) server = "none"
//
//        if (client == null) client = "none"
//
//        val servers = arrayOf(client, server)
//        val serversString = servers.joinToString(prefix = "{", postfix = "}", separator = ",")
//
//        val measurement = MeasurementEntity(
//            deviceId = deviceId,
//            deviceManufacturer = deviceMod?.manufacturer,
//            deviceModel = deviceMod?.model,
//            deviceOsName = "Android",
//            deviceOsVersion = deviceMod?.osVersion,
//            appName = appMod?.appName,
//            provider = simMod?.carrier,
//            type = direction.toString().lowercase(),
//            duration = totalDuration,
//            scheduled = false,
//            success = result.success,
//            carrierAggregation = false,
//            networkAvailable = true,
//            networkConnected = true,
//            networkRoaming = false
//        )
//        Log.d(TAG,"measurement = $measurement")
//
//        var location: android.location.Location? = null
//
//        Locus.getCurrentLocation(context!!) { locationResult ->
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
//
//        val insertedMeasurement = measurementRepository?.insertMeasurement(measurement)
////        val insertedMeasurement = measurementNetworkDatasource?.insertMeasurement(measurement)
//    }

//    suspend fun createMeasurement(result: Ndt8TestResult, direction: Ndt8TestDirection) {
//        var location: android.location.Location? = null
//
//        Locus.getCurrentLocation(context!!) { locationResult ->
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
//
//        writeMessage("*** Attempting to store Measurement to Supabase ***")
//
//        val supabase = createSupabaseClient(
//            supabaseUrl = "https://xepxxvpbexkyxrwtrgqv.supabase.co",
//            supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InhlcHh4dnBiZXhreXhyd3RyZ3F2Iiwicm9sZSI6ImFub24iLCJpYXQiOjE2Nzc3NjgwOTgsImV4cCI6MTk5MzM0NDA5OH0.4yPsO_M4VPJu_wt4EVcOa3Y2_paj2I_1IRKujmIVjdA"
//        ) {
//            install(Postgrest)
//        }
//        val dataTable = supabase.postgrest["upload_download_data"]
//        val measurementTable = supabase.postgrest["measurements"]
//        val locationTable = supabase.postgrest["locations"]
//
//        val warmupDuration = if (result.warmupMetrics != null)
//            result.warmupMetrics.usecs else 0
//
//        val activeDuration = if (result.activeMetrics != null)
//            result.activeMetrics.usecs else 0
//
//        val totalDuration = warmupDuration + activeDuration
//
//        var server = if (result.streamResults.isNotEmpty())
//            result.streamResults.first()?.remoteAddr else "server"
//
//        var client = if (result.streamResults.isNotEmpty())
//            result.streamResults.first()?.localAddr else "client"
//
//        if (server == null) server = "none"
//
//        if (client == null) client = "none"
//
//        val servers = arrayOf(client, server)
//        val serversString = servers.joinToString(prefix = "{", postfix = "}", separator = ",")
//
//        println("********** Servers: $serversString")
//
//        val dataStore = LocalDataStore(context!!)
//
//        var deviceId = runBlocking {
//            dataStore.getDeviceId.first()
//        }
//
//        val jsonMeasurement = buildJsonObject {
//            put("id", UUID.randomUUID().toString())
//            put("device_id", deviceId)
//            put("device_manufacturer", deviceMod?.manufacturer)
//            put("device_model", deviceMod?.model)
//            put("device_os_name", "Android")
//            put("device_os_version", deviceMod?.osVersion)
//            put("app_name", appMod?.appName)
//            put("provider", simMod?.carrier)
//            put("type", direction.toString().lowercase())
//            put("duration", totalDuration)
//            put("success", result.success)
//            put("scheduled", false)
//            put("carrier_aggregation", false)
//            put("network_connected", true)
//            put("network_available", true)
//            put("network_roaming", false)
////            put("data_id", insertedData.id)
//        }
//
//        writeMessage("********************* Measurement = $jsonMeasurement")
//        println(jsonMeasurement)
//
//        val insertedMeasurement = measurementTable.insert(jsonMeasurement).decodeSingle<MeasurementEntity>()
//
//        writeMessage("*** Inserted new Measurement record: $insertedMeasurement")
//        println("*** Inserted new Measurement record: $insertedMeasurement")
//
//        val jsonMeasurementData = buildJsonObject {
//            put("measurement_id", insertedMeasurement.id)
//            put("warmup_duration", result.warmupMetrics?.usecs)
//            put("warmup_bytes", result.warmupMetrics?.bytes)
//            put("duration", result.activeMetrics?.usecs)
//            put("bytes", result.activeMetrics?.bytes)
//            put("servers", serversString)
//        }
//        println("*********** jsonMeasurementData: $jsonMeasurementData")
//
//        val insertedData = dataTable.insert(jsonMeasurementData).decodeSingle<UploadDownloadDataEntity>()
//        writeMessage("Data: $insertedData")
//        println("Data: $insertedData")
//
//        val locationPermission = ActivityCompat.checkSelfPermission(
//            activity!!,
//            Manifest.permission.ACCESS_FINE_LOCATION
//        )
//
//        location?.let {
//            val jsonLocation = buildJsonObject {
//                put("measurement_id", insertedMeasurement.id)
//                put("lat", it.latitude)
//                put("lon", it.longitude)
//                put("accuracy", if (it.hasAccuracy()) it.accuracy else null)
//                put("speed", if (it.hasSpeed()) it.speed else null)
////                put("speedAccuracy", if (it.hasSpeedAccuracy()) it.speedAccuracyMetersPerSecond else null)
//                put("heading", if (it.hasBearing()) it.bearing else null)
//                put("measurement_id", insertedMeasurement.id)
//            }
//            val insertedLocationEntity = locationTable.insert(jsonLocation).decodeSingle<LocationEntity>()
//            writeMessage("*** Inserted new Location record: $insertedLocationEntity")
//            Log.d(TAG, "*** Inserted new Location record: $insertedLocationEntity")
//        }
//    }

    fun toggleButton(enabled: Boolean) {
        val handler = Handler(Looper.getMainLooper())
        handler.post {
            binding.buttonFirst.setText(if (enabled) R.string.measure else R.string.measuring)
            binding.buttonFirst.isEnabled = enabled
        }
    }

    fun writeMessage(m: String) {
        val handler = Handler(Looper.getMainLooper())
        handler.post {
            binding.textviewFirst.append("\n> $m")
        }
    }

    fun updateSpeedometer(bytesPerSec: Double, moveDuration: Long = 200) {
        val handler = Handler(Looper.getMainLooper())
        handler.post {
            speedometer.speedTo(bytesPerSec.toFloat(), moveDuration)
        }
    }
}