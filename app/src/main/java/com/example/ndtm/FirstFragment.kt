package com.example.ndtm

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.ndtm.databinding.FragmentFirstBinding
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import okhttp3.OkHttpClient
import org.json.JSONArray
import java.util.*
import kotlin.concurrent.thread


/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class FirstFragment : Fragment() {
    private val TAG = this::class.simpleName
    private var _binding: FragmentFirstBinding? = null

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

        binding.textviewFirst.movementMethod = ScrollingMovementMethod()

        binding.buttonFirst.setOnClickListener {
            thread {
                toggleButton(false)

                try {
                    runTestSequence()
                } catch (e: Exception) {
                    Log.e(TAG, "unexpected error running test sequence", e)
                    writeMessage("unexpected error running test sequence: ${e.localizedMessage}")
                } finally {
                    writeMessage("*** Done with Upload/Download Test ***");
                }

                toggleButton(true)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun runTestSequence() {
        val measurementId = UUID.randomUUID().toString()
        writeMessage("RUNNING TEST SEQUENCE with measurement id $measurementId")

        val client = OkHttpClient.Builder().build()

        writeMessage("selecting server")
        val server = try {
            selectServer(client, "https://locate.mlab-sandbox.measurementlab.net/v2/nearest/")
        } catch (t: Throwable) {
            Log.d(TAG, "failed to select server", t)
            writeMessage("failed to select server: ${t.localizedMessage}")
            return
        }
        writeMessage("selected server ${server.machine} in ${server.location}")

        runTest(client, server, measurementId, NdtMTestDirection.DOWNLOAD)
        runTest(client, server, measurementId, NdtMTestDirection.UPLOAD)
    }

    private fun runTest(
        client: OkHttpClient,
        server: NdtMLocateServer,
        measurementId: String,
        direction: NdtMTestDirection
    ) {
        val dir = if (direction == NdtMTestDirection.DOWNLOAD) "download" else "upload"
        writeMessage("running $dir test")
        val result = try {
            val test = NdtMTestComponent(client, server, measurementId, direction, 3)
            runBlocking {
                launch {
                    test.progress.consumeEach {
                        Log.d(TAG, "got progress $it")
                        writeMessage("progress: $it")
                    }
                }

                test.run()
            }
        } catch (t: Throwable) {
            Log.d(TAG, "$dir test failed", t)
            writeMessage("$dir test failed: ${t.localizedMessage}")
            return
        }

//        val result = NdtMTestResult(
//            success = true,
//            activeMetrics = NdtMTestMetrics(bytes = 65535, usecs = 12345678, bytesPerSec = 304.0),
//            measurements = emptyList(),
//            warmupMetrics = null
//        )
        Log.i(TAG, "$dir test complete: $result")
        writeMessage("$dir test complete: ${if (result.success) "success" else "failure"}; warmup ${result.warmupMetrics}; active ${result.activeMetrics}")

        runBlocking { createMeasurement(result, direction) }
    }

    suspend fun createMeasurement(result: NdtMTestResult, direction: NdtMTestDirection) {
        val gson = GsonBuilder().setPrettyPrinting().create()

//        println("NtdMTestResult:")
//        println(gson.toJson(result))

        writeMessage("*** Attempting to store Measurement to Supabase ***")

        val supabase = createSupabaseClient(
            supabaseUrl = "https://xepxxvpbexkyxrwtrgqv.supabase.co",
            supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InhlcHh4dnBiZXhreXhyd3RyZ3F2Iiwicm9sZSI6ImFub24iLCJpYXQiOjE2Nzc3NjgwOTgsImV4cCI6MTk5MzM0NDA5OH0.4yPsO_M4VPJu_wt4EVcOa3Y2_paj2I_1IRKujmIVjdA"
        ) {
            install(Postgrest)
        }
        val dataTable = supabase.postgrest["upload_download_data"];
        val measurementTable = supabase.postgrest["measurements"];

        val warmupDuration = if (result.warmupMetrics != null)
            result.warmupMetrics.usecs else 0

        val activeDuration = if (result.activeMetrics != null)
            result.activeMetrics.usecs else 0

        val totalDuration = warmupDuration + activeDuration

        var server = if (result.measurements.isNotEmpty())
            result.measurements.first().first().ConnectionInfo?.Server else ""

        if (server == null) server = "none"

        var client = if (result.measurements.isNotEmpty())
            result.measurements.first().first().ConnectionInfo?.Client else ""

        if (client == null) client = "none"

        val servers = arrayOf(client, server)
        val serversString = servers.joinToString(prefix = "{", postfix = "}", separator = ",")

        println("********** Servers: $serversString")

        val jsonMeasurementData = buildJsonObject {
            put("warmup_duration", result.warmupMetrics?.usecs)
            put("warmup_bytes", result.warmupMetrics?.bytes)
            put("duration", result.activeMetrics?.usecs)
            put("bytes", result.activeMetrics?.bytes)
            put("servers", serversString)
        }
        println("*********** jsonMeasurementData: $jsonMeasurementData")

        val insertedData = dataTable.insert(jsonMeasurementData).decodeSingle<UploadDownloadData>()
        writeMessage("Data: $insertedData")
        println("Data: $insertedData")

        val jsonMeasurement = buildJsonObject {
            put("device_manufacturer", "Google")
            put("device_model", "Pixel 6a")
            put("device_os_name", "Android")
            put("device_os_version", "13")
            put("app_name", "ntdm")
            put("provider", "AT&T")
            put("type", direction.toString().lowercase())
            put("duration", totalDuration)
            put("success", result.success)
            put("scheduled", false)
            put("carrier_aggregation", false)
            put("network_connected", true)
            put("network_available", true)
            put("network_roaming", false)
            put("data_id", insertedData.id)
        }

        writeMessage("Measurement = $jsonMeasurement")
        println(jsonMeasurement)

//        writeMessage(gson.toJson(measurement))

        val insertedMeasurement = measurementTable.insert(jsonMeasurement).decodeSingle<Measurement>()
//        val insertedMeasurement = table.insert(Json.encodeToString(measurement)).decodeSingle<Measurement>()

        writeMessage("*** Inserted new Measurement record: $insertedMeasurement")
        println("*** Inserted new Measurement record: $insertedMeasurement")
    }

    fun toggleButton(enabled: Boolean) {
        val handler = Handler(Looper.getMainLooper())
        handler.post {
            binding.buttonFirst.setText(if (enabled) R.string.measure else R.string.measuring)
            binding.buttonFirst.isEnabled = enabled
        }
    }

    fun writeMessage(m: String) {
        val handler = Handler(Looper.getMainLooper())
        handler.post { binding.textviewFirst.append("\n> $m") }
    }
}