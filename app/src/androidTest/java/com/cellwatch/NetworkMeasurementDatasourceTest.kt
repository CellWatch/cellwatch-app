package com.cellwatch

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cellwatch.data.model.LatencyData
import com.cellwatch.data.model.Location
import com.cellwatch.data.model.Measurement
import com.cellwatch.data.model.UploadDownloadData
import com.cellwatch.data.model.asNetworkModel
import com.cellwatch.data.network.NetworkMeasurementDatasource
import com.cellwatch.data.network.model.NetworkMeasurement
import com.cellwatch.data.network.model.asExternalModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.PostgrestBuilder
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class NetworkMeasurementDatasourceTest {
    private lateinit var networkMeasurementDatasource: NetworkMeasurementDatasource

    private lateinit var supabaseClient: SupabaseClient

    private lateinit var measurementTable: PostgrestBuilder
    private lateinit var locationTable: PostgrestBuilder
    private lateinit var dataTable: PostgrestBuilder
    private lateinit var latencyTable: PostgrestBuilder
    
    companion object {
        private const val TAG = "MeasurementNetworkDatasourceTest"
    }
    
    @Before
    fun createMeasurementNetworkDatasource() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val supabaseUrl = BuildConfig.SUPABASE_URL
        val supabaseApiKey = BuildConfig.SUPABASE_API_KEY

        networkMeasurementDatasource = NetworkMeasurementDatasource

        supabaseClient = createSupabaseClient(
                supabaseUrl = supabaseUrl, //"https://xepxxvpbexkyxrwtrgqv.supabase.co",
                supabaseKey = supabaseApiKey //"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZS1kZW1vIiwicm9sZSI6ImFub24iLCJleHAiOjE5ODM4MTI5OTZ9.CRXP1A7WOeoJeXxjNni43kdQwgnWNReilDMblYTn_I0"
//                        supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InhlcHh4dnBiZXhreXhyd3RyZ3F2Iiwicm9sZSI6ImFub24iLCJpYXQiOjE2Nzc3NjgwOTgsImV4cCI6MTk5MzM0NDA5OH0.4yPsO_M4VPJu_wt4EVcOa3Y2_paj2I_1IRKujmIVjdA"
        ) {
            install(Postgrest)
        }

        measurementTable = supabaseClient.postgrest["measurements"]
        locationTable = supabaseClient.postgrest["locations"]
        dataTable = supabaseClient.postgrest["upload_download_data"]
        latencyTable = supabaseClient.postgrest["latency_data"]
    }

    @Test
    @Throws(Exception::class)
    fun measurementInsert() {
        var insertedMeasurement: Measurement? = null
        val measurement = Measurement(
            groupId = UUID.randomUUID().toString(),
//            campaignId = UUID.randomUUID().toString(),
//            sessionId = UUID.randomUUID().toString(),
            deviceId = UUID.randomUUID().toString(),
            deviceManufacturer = "Google",
            deviceModel = "Pixel 5",
            deviceOsName = "Android",
            deviceOsVersion = "13",
            appName = "CellWatch",
            provider = "T-Mobile",
            type = "download", //direction.toString().lowercase(),
            timestamp = Clock.System.now(),
            duration = 7288314,
            scheduled = false,
            success = true,
            carrierAggregation = false,
            networkAvailable = true,
            networkConnected = true,
            networkRoaming = false,
            extraData = "extraData"
        )

        try {
            insertedMeasurement =
                runBlocking {
                    measurementTable.insert(measurement.asNetworkModel())
                        .decodeSingle<NetworkMeasurement>().asExternalModel()
                }
            Log.d(
                TAG,
                "*** Inserted new Measurement record: $insertedMeasurement"
            )
        } catch (e: RestException) {
            Log.e(TAG, "RestException: ${e.message}")
            throw e
        } catch (e: HttpRequestTimeoutException) {
            Log.e(TAG, "HttpRequestTimeoutException: ${e.message}")
            throw e
        } catch (e: HttpRequestException) {
            Log.e(TAG, "HttpRequestException: ${e.message}")
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Exception: ${e.message}")
            throw e
        }
    }

        @Test
    @Throws(Exception::class)
    fun MeasurementNetworkDatasourceInsert() {
        val deviceId = UUID.randomUUID().toString()
        val groupId = UUID.randomUUID().toString()

        val location = Location(
            timestamp = Clock.System.now(),
            lat = 33.87797,
            lon = -84.3111,
            accuracy = 20.3,
            speed = .123,
            heading = 234.23
        )

        val downloadLocations = listOf<Location>(
            Location(
                timestamp = Clock.System.now(),
                lat = 33.87297,
                lon = -84.3413,
                accuracy = 20.883,
                speed = .323,
                heading = 234.98
            ),
            Location(
                timestamp = Clock.System.now(),
                lat = 33.87797,
                lon = -84.3111,
                accuracy = 20.3,
                speed = .123,
                heading = 234.23
            ),
            Location(
                timestamp = Clock.System.now(),
                lat = 33.5797,
                lon = -84.6411,
                accuracy = 18.55,
                speed = .623,
                heading = 204.663
            )
        )

        val uploadLocations = listOf<Location>(
            Location(
                timestamp = Clock.System.now(),
                lat = 33.297,
                lon = -84.13,
                accuracy = 20.45883,
                speed = 0.1,
                heading = 234.44
            ),
            Location(
                timestamp = Clock.System.now(),
                lat = 33.4797,
                lon = -84.5111,
                accuracy = 20.93,
                speed = .23,
                heading = 233.3
            ),
            Location(
                timestamp = Clock.System.now(),
                lat = 33.5597,
                lon = -84.9011,
                accuracy = 18.15,
                speed = .523,
                heading = 214.663
            )
        )

        val latencyLocations = listOf<Location>(
            Location(
                timestamp = Clock.System.now(),
                lat = 33.297,
                lon = -84.13,
                accuracy = 20.45883,
                speed = 0.1,
                heading = 234.44
            ),
            Location(
                timestamp = Clock.System.now(),
                lat = 33.4797,
                lon = -84.5111,
                accuracy = 20.93,
                speed = .23,
                heading = 233.3
            ),
            Location(
                timestamp = Clock.System.now(),
                lat = 33.5597,
                lon = -84.9011,
                accuracy = 18.15,
                speed = .523,
                heading = 214.663
            )
        )

        val uploadData = UploadDownloadData(
            warmupDuration = 10234,
            warmupBytes = 134425,
            duration = 9372444,
            bytes = 83724,
            servers = listOf("server1", "server2")
        )

        val downloadData = UploadDownloadData(
            warmupDuration = 12344,
            warmupBytes = 56325,
            duration = 3756444,
            bytes = 53724,
            servers = listOf("server1", "server2")
        )

        val latencyData = LatencyData(
            rtt = 12355,
            jitter = 88372,
            sent = 8124553,
            received = 779927,
            servers = listOf("server1", "server2")
        )

        val latencyMeasurement = Measurement(
//            id = UUID.randomUUID().toString(),
            groupId = groupId,
//            campaignId = UUID.randomUUID().toString(),
//            sessionId = UUID.randomUUID().toString(),
            deviceId = deviceId,
            deviceManufacturer = "Google",
            deviceModel = "Pixel 5",
            deviceOsName = "Android",
            deviceOsVersion = "13",
            appName = "CellWatch",
            provider = "T-Mobile",
            type = "latency", //direction.toString().lowercase(),
            timestamp = Clock.System.now(),
            duration = 7288314,
            scheduled = false,
            success = true,
            carrierAggregation = false,
            networkAvailable = true,
            networkConnected = true,
            networkRoaming = false,
            extraData = "extraData",
            latencyData = latencyData,
            locations = latencyLocations
        )

        val downloadMeasurement = Measurement(
//            id = UUID.randomUUID().toString(),
            groupId = groupId,
//            campaignId = UUID.randomUUID().toString(),
//            sessionId = UUID.randomUUID().toString(),
            deviceId = deviceId,
            deviceManufacturer = "Google",
            deviceModel = "Pixel 5",
            deviceOsName = "Android",
            deviceOsVersion = "13",
            appName = "CellWatch",
            provider = "T-Mobile",
            type = "download", //direction.toString().lowercase(),
            timestamp = Clock.System.now(),
            duration = 7288314,
            scheduled = false,
            success = true,
            carrierAggregation = false,
            networkAvailable = true,
            networkConnected = true,
            networkRoaming = false,
            extraData = "extraData",
            uploadDownloadData = downloadData,
            locations = downloadLocations
        )

        val uploadMeasurement = Measurement(
//            id = UUID.randomUUID().toString(),
            groupId = groupId,
//            campaignId = UUID.randomUUID().toString(),
//            sessionId = UUID.randomUUID().toString(),
            deviceId = deviceId,
            deviceManufacturer = "Google",
            deviceModel = "Pixel 5",
            deviceOsName = "Android",
            deviceOsVersion = "13",
            appName = "CellWatch",
            provider = "T-Mobile",
            type = "upload", //direction.toString().lowercase(),
            timestamp = Clock.System.now(),
            duration = 8874314,
            scheduled = false,
            success = true,
            carrierAggregation = false,
            networkAvailable = true,
            networkConnected = true,
            networkRoaming = false,
            extraData = "extraData",
            uploadDownloadData = uploadData,
            locations = uploadLocations
        )

        var insertedMeasurement: Measurement? = null
        var insertedLocation: Location? = null
        var insertedData: UploadDownloadData? = null

        Log.d(TAG, "latencyMeasurement = $latencyMeasurement")

        try {
            Log.d(TAG, "Attempting to insert LatencyData Measurement")
            insertedMeasurement = runBlocking {
                networkMeasurementDatasource.insertMeasurement(latencyMeasurement)
            }

            Log.d(TAG,"*** Inserted new Latency Measurement record: $insertedMeasurement")
//            Log.d(TAG, "*** Inserted new UploadDownloadData record: $insertedData")
        } catch (e: Exception) {
            Log.e(TAG, "Error inserting measurement record", e)
            throw e
        }

//        Log.d(TAG, "*** Inserted Measurement: $insertedMeasurement")
//        Log.d(TAG, "*** Inserted Location: $insertedLocationEntity")

        try {
            insertedMeasurement = runBlocking {
                networkMeasurementDatasource.insertMeasurement(uploadMeasurement)
            }

            Log.d(TAG,"*** Inserted new Upload Measurement record: $insertedMeasurement")
//            Log.d(TAG, "*** Inserted new UploadDownloadData record: $insertedData")
        } catch (e: Exception) {
            Log.e(TAG, "Error inserting measurement record", e)
            throw e
        }

        Log.d(TAG, "*** Inserted Measurement: $insertedMeasurement")
        Log.d(TAG, "*** Inserted Location: $insertedLocation")

        try {
            insertedMeasurement = runBlocking {
                networkMeasurementDatasource.insertMeasurement(downloadMeasurement)
            }

            Log.d(TAG,"*** Inserted new Download Measurement record: $insertedMeasurement")
//            Log.d(TAG, "*** Inserted new UploadDownloadData record: $insertedData")
        } catch (e: Exception) {
            Log.e(TAG, "Error inserting measurement record", e)
            throw e
        }

//        Log.d(TAG, "*** Inserted Measurement: $insertedMeasurement")
//        Log.d(TAG, "*** Inserted Location: $insertedLocationEntity")
    }
}