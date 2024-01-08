package edu.gatech.cc.cellwatch

import edu.gatech.cc.cellwatch.core.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import edu.gatech.cc.cellwatch.data.model.Cell
import edu.gatech.cc.cellwatch.data.model.LatencyData
import edu.gatech.cc.cellwatch.data.model.Location
import edu.gatech.cc.cellwatch.data.model.Measurement
import edu.gatech.cc.cellwatch.data.model.UploadDownloadData
import edu.gatech.cc.cellwatch.data.model.asNetworkModel
import edu.gatech.cc.cellwatch.data.network.NetworkMeasurementDatasource
import edu.gatech.cc.cellwatch.data.network.model.NetworkMeasurement
import edu.gatech.cc.cellwatch.data.network.model.NetworkMeasurementWithData
import edu.gatech.cc.cellwatch.data.network.model.asExternalModel
import com.google.gson.GsonBuilder
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.PostgrestBuilder
import io.github.jan.supabase.postgrest.query.PostgrestResult
import io.github.jan.supabase.postgrest.rpc
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import org.junit.Assert.assertEquals
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

    private val groupId = UUID.randomUUID().toString()
    
    @Before
    fun createMeasurementNetworkDatasource() {
        val supabaseUrl = BuildConfig.SUPABASE_URL
        val supabaseApiKey = BuildConfig.SUPABASE_API_KEY

        networkMeasurementDatasource = NetworkMeasurementDatasource

        supabaseClient = createSupabaseClient(
                supabaseUrl = supabaseUrl, //"https://xepxxvpbexkyxrwtrgqv.supabase.co",
                supabaseKey = supabaseApiKey //"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZS1kZW1vIiwicm9sZSI6ImFub24iLCJleHAiOjE5ODM4MTI5OTZ9.CRXP1A7WOeoJeXxjNni43kdQwgnWNReilDMblYTn_I0"
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
            simMcc = "310",
            simMnc = "310",
            netMcc = "410",
            netMnc = "410",
            extraData = "extraData"
        )

        try {
            var result: PostgrestResult
            insertedMeasurement =
                runBlocking {
                    result = measurementTable.insert(measurement.asNetworkModel())

                    result.decodeSingle<NetworkMeasurement>().asExternalModel()
                }
            Log.d(
                TAG,
                "*** Inserted new Measurement record: $result"
            )
        } catch (e: RestException) {
            Log.d(TAG, "RestException: ${e.message}")
            throw e
        } catch (e: HttpRequestTimeoutException) {
            Log.d(TAG, "HttpRequestTimeoutException: ${e.message}")
            throw e
        } catch (e: HttpRequestException) {
            Log.d(TAG, "HttpRequestException: ${e.message}")
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "Exception: ${e.message}")
            throw e
        }
    }

    @Test
    @Throws(Exception::class)
    fun InsertMeasurementAndDataTransaction() {
        var insertedDownloadMeasurement: Measurement?
        var insertedUploadMeasurement: Measurement?
        var insertedLatencyMeasurement: Measurement?

        val deviceId = UUID.randomUUID().toString()
//        val groupId = UUID.randomUUID().toString()

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
            )
        )

        val uploadCells = listOf<Cell>(
            Cell(
                timestamp = Clock.System.now(),
                cellId = 234,
                physicalCellId = 4321,
                cellConnection = 1,
                networkGeneration = "5G",
                networkSubtype = "GSM",
                signalStrength = -103,
                rssi = -78,
                rsrp = -102,
                rsrq = -12,
                sinr = 2,
                csiRsrp = -101,
                csiRsrq = -13,
                csiSinr = 2,
                cqi = 4,
                spectrumBand = "n41",
                spectrumBandwidth = 100.0f,
                arfcn = 528000
                ),
            Cell(
                timestamp = Clock.System.now(),
                cellId = 235,
                physicalCellId = 4322,
                cellConnection = 1,
                networkGeneration = "5G",
                networkSubtype = "GSM",
                signalStrength = -102,
                rssi = -79,
                rsrp = -101,
                rsrq = -11,
                sinr = 2,
                csiRsrp = -100,
                csiRsrq = -14,
                csiSinr = 2,
                cqi = 4,
                spectrumBand = "n41",
                spectrumBandwidth = 100.0f,
                arfcn = 528000
            )
        )

        val downloadCells = listOf<Cell>(
            Cell(
                timestamp = Clock.System.now(),
                cellId = 234,
                physicalCellId = 4321,
                cellConnection = 1,
                networkGeneration = "5G",
                networkSubtype = "GSM",
                signalStrength = -103,
                rssi = -78,
                rsrp = -102,
                rsrq = -12,
                sinr = 2,
                csiRsrp = -101,
                csiRsrq = -13,
                csiSinr = 2,
                cqi = 4,
                spectrumBand = "n41",
                spectrumBandwidth = 100.0f,
                arfcn = 528000
            ),
            Cell(
                timestamp = Clock.System.now(),
                cellId = 235,
                physicalCellId = 4322,
                cellConnection = 1,
                networkGeneration = "5G",
                networkSubtype = "GSM",
                signalStrength = -102,
                rssi = -79,
                rsrp = -101,
                rsrq = -11,
                sinr = 2,
                csiRsrp = -100,
                csiRsrq = -14,
                csiSinr = 2,
                cqi = 4,
                spectrumBand = "n41",
                spectrumBandwidth = 100.0f,
                arfcn = 528000
            )
        )

        val latencyCells = listOf<Cell>(
            Cell(
                timestamp = Clock.System.now(),
                cellId = 234,
                physicalCellId = 4321,
                cellConnection = 1,
                networkGeneration = "5G",
                networkSubtype = "GSM",
                signalStrength = -103,
                rssi = -78,
                rsrp = -102,
                rsrq = -12,
                sinr = 2,
                csiRsrp = -101,
                csiRsrq = -13,
                csiSinr = 2,
                cqi = 4,
                spectrumBand = "n41",
                spectrumBandwidth = 100.0f,
                arfcn = 528000
            ),
            Cell(
                timestamp = Clock.System.now(),
                cellId = 235,
                physicalCellId = 4322,
                cellConnection = 1,
                networkGeneration = "5G",
                networkSubtype = "GSM",
                signalStrength = -102,
                rssi = -79,
                rsrp = -101,
                rsrq = -11,
                sinr = 2,
                csiRsrp = -100,
                csiRsrq = -14,
                csiSinr = 2,
                cqi = 4,
                spectrumBand = "n41",
                spectrumBandwidth = 100.0f,
                arfcn = 528000
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
            )
        )

        val uploadData = UploadDownloadData(
            warmupDuration = 10234,
            warmupBytes = 134425,
            duration = 9372444,
            bytes = 83724,
            applicationBytes = 92331,
            servers = listOf("server1", "server2")
        )

        val downloadData = UploadDownloadData(
            warmupDuration = 12344,
            warmupBytes = 56325,
            duration = 3756444,
            bytes = 53724,
            applicationBytes = 92331,
            servers = listOf("server1", "server2")
        )

        val latencyData = LatencyData(
            rtt = 12355,
            jitter = 88372,
            sent = 8124553,
            received = 779927,
            servers = listOf("server1", "server2")
        )

        val downloadMeasurement = Measurement(
//            id = UUID.randomUUID().toString(),
            groupId = groupId,
            campaignId = UUID.randomUUID().toString(),
            sessionId = UUID.randomUUID().toString(),
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
            simMcc = "310",
            simMnc = "310",
            netMcc = "410",
            netMnc = "410",
            extraData = "extraData"
        )

        val uploadMeasurement = Measurement(
//            id = UUID.randomUUID().toString(),
            groupId = groupId,
            campaignId = UUID.randomUUID().toString(),
            sessionId = UUID.randomUUID().toString(),
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
            simMcc = "310",
            simMnc = "310",
            netMcc = "410",
            netMnc = "410",
            extraData = "extraData",
        )

        val latencyMeasurement = Measurement(
//            id = UUID.randomUUID().toString(),
            groupId = groupId,
            campaignId = UUID.randomUUID().toString(),
            sessionId = UUID.randomUUID().toString(),
            deviceId = deviceId,
            deviceManufacturer = "Google",
            deviceModel = "Pixel 5",
            deviceOsName = "Android",
            deviceOsVersion = "13",
            appName = "CellWatch",
            provider = "T-Mobile",
            type = "latency", //direction.toString().lowercase(),
            timestamp = Clock.System.now(),
            duration = 742314,
            scheduled = false,
            success = true,
            carrierAggregation = false,
            networkAvailable = true,
            networkConnected = true,
            networkRoaming = false,
            simMcc = "310",
            simMnc = "310",
            netMcc = "410",
            netMnc = "410",
            extraData = "extraData",
        )

        val uploadMeasurementData = NetworkMeasurementWithData(
            uploadMeasurement.asNetworkModel(),
            uploadData.asNetworkModel(),
            null,
//            uploadLatencyData.asNetworkModel(),
            uploadLocations.map { location -> location.asNetworkModel() },
            uploadCells.map { cell -> cell.asNetworkModel() }
        )

        val downloadMeasurementData = NetworkMeasurementWithData(
            downloadMeasurement.asNetworkModel(),
            downloadData.asNetworkModel(),
            null,
//            downloadLatencyData.asNetworkModel(),
            downloadLocations.map { location -> location.asNetworkModel() },
            downloadCells.map { cell -> cell.asNetworkModel() }
        )
        val latencyMeasurementData = NetworkMeasurementWithData(
            latencyMeasurement.asNetworkModel(),
            null,
            latencyData.asNetworkModel(),
            latencyLocations.map { location -> location.asNetworkModel() },
            latencyCells.map { cell -> cell.asNetworkModel() }

        )

//        Log.d(TAG, "measurementData = $measurementData")
        var result: PostgrestResult
        try {
            insertedUploadMeasurement =
                runBlocking {
                    supabaseClient.postgrest.rpc("insert_measurement", uploadMeasurementData).decodeAs<NetworkMeasurement>().asExternalModel()
                }

            insertedDownloadMeasurement =
                runBlocking {
                    supabaseClient.postgrest.rpc("insert_measurement", downloadMeasurementData).decodeAs<NetworkMeasurement>().asExternalModel()
                }
            insertedLatencyMeasurement =
                runBlocking {
                    supabaseClient.postgrest.rpc("insert_measurement", latencyMeasurementData).decodeAs<NetworkMeasurement>().asExternalModel()
                }
        } catch (e: RestException) {
            Log.d(TAG, "RestException: ${e.message}")
            throw e
        } catch (e: HttpRequestTimeoutException) {
            Log.d(TAG, "HttpRequestTimeoutException: ${e.message}")
            throw e
        } catch (e: HttpRequestException) {
            Log.d(TAG, "HttpRequestException: ${e.message}")
            throw e
        } catch (e: Exception) {
            Log.d(TAG, "Exception: ${e.message}")
            throw e
        }
//        insertedMeasurement = result.decodeAs<NetworkMeasurement>().asExternalModel()

        assertEquals(insertedUploadMeasurement.groupId, groupId);

        Log.d(TAG, "*** Inserted Upload Measurement: $insertedUploadMeasurement")
        Log.d(TAG, "*** Inserted Download Measurement: $insertedDownloadMeasurement")
        Log.d(TAG, "*** Inserted Latency Measurement: $insertedLatencyMeasurement")
    }

    @Test
    @Throws(Exception::class)
    fun GetMeasurementsByGroupId() {
        var measurements: List<Measurement>?
        val gson = GsonBuilder().setPrettyPrinting().create()

        try {
            measurements = runBlocking {
                networkMeasurementDatasource.getMeasurements()
            }
        } catch (e: Exception) {
            Log.d(NetworkMeasurementSubmissionTest.TAG, "Error inserting measurement record", e)
            throw e
        }

        Log.d(NetworkMeasurementSubmissionTest.TAG, "*** Got all Measurements: ${measurements?.count()}")

        measurements?.forEach { measurement ->
            val jsonString = gson.toJson(measurement.asNetworkModel())
            Log.d(NetworkMeasurementSubmissionTest.TAG, jsonString)
        }
    }

    @Test
    @Throws(Exception::class)
    fun MeasurementNetworkDatasourceInsert() {
        val deviceId = UUID.randomUUID().toString()
        val groupId = UUID.randomUUID().toString()
        val campaignId = UUID.randomUUID().toString()
        val sessionId = UUID.randomUUID().toString()

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
            applicationBytes = 92331,
            servers = listOf("server1", "server2")
        )

        val downloadData = UploadDownloadData(
            warmupDuration = 12344,
            warmupBytes = 56325,
            duration = 3756444,
            bytes = 53724,
            applicationBytes = 92331,
            servers = listOf("server1", "server2")
        )

        val latencyData = LatencyData(
            rtt = 12355,
            jitter = 88372,
            sent = 8124553,
            received = 779927,
            servers = listOf("server1", "server2")
        )

        val downloadCells = listOf<Cell>(
            Cell(
                timestamp = Clock.System.now(),
                cellId = 234,
                physicalCellId = 4321,
                cellConnection = 1,
                networkGeneration = "5G",
                networkSubtype = "GSM",
                signalStrength = -103,
                rssi = -78,
                rsrp = -102,
                rsrq = -12,
                sinr = 2,
                csiRsrp = -101,
                csiRsrq = -13,
                csiSinr = 2,
                cqi = 4,
                spectrumBand = "n41",
                spectrumBandwidth = 100.0f,
                arfcn = 528000
            ),
            Cell(
                timestamp = Clock.System.now(),
                cellId = 235,
                physicalCellId = 4322,
                cellConnection = 1,
                networkGeneration = "5G",
                networkSubtype = "GSM",
                signalStrength = -102,
                rssi = -79,
                rsrp = -101,
                rsrq = -11,
                sinr = 2,
                csiRsrp = -100,
                csiRsrq = -14,
                csiSinr = 2,
                cqi = 4,
                spectrumBand = "n41",
                spectrumBandwidth = 100.0f,
                arfcn = 528000
            )
        )

        val latencyMeasurement = Measurement(
//            id = UUID.randomUUID().toString(),
            groupId = groupId,
            campaignId = campaignId,
            sessionId = sessionId,
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
            simMcc = "310",
            simMnc = "310",
            netMcc = "410",
            netMnc = "410",
            extraData = "extraData",
            latencyData = latencyData,
            locations = latencyLocations
        )

        val downloadMeasurement = Measurement(
//            id = UUID.randomUUID().toString(),
            groupId = groupId,
            campaignId = campaignId,
            sessionId = sessionId,
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
            simMcc = "310",
            simMnc = "310",
            netMcc = "410",
            netMnc = "410",
            extraData = "extraData",
            uploadDownloadData = downloadData,
            cells = downloadCells,
            locations = downloadLocations
        )

        val uploadMeasurement = Measurement(
//            id = UUID.randomUUID().toString(),
            groupId = groupId,
            campaignId = campaignId,
            sessionId = sessionId,
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
            simMcc = "310",
            simMnc = "310",
            netMcc = "410",
            netMnc = "410",
            extraData = "extraData",
            uploadDownloadData = uploadData,
            locations = uploadLocations
        )

        var insertedMeasurement: Measurement? = null

        Log.d(TAG, "latencyMeasurement = $latencyMeasurement")

        try {
            Log.d(TAG, "Attempting to insert LatencyData Measurement")
            insertedMeasurement = runBlocking {
                networkMeasurementDatasource.insertMeasurement(latencyMeasurement)
            }
        } catch (e: Exception) {
            Log.d(TAG, "Error inserting measurement record", e)
            throw e
        }
        Log.d(TAG,"*** Inserted new Latency Measurement record: $insertedMeasurement")

        try {
            insertedMeasurement = runBlocking {
                networkMeasurementDatasource.insertMeasurement(uploadMeasurement)
            }
        } catch (e: Exception) {
            Log.d(TAG, "Error inserting measurement record", e)
            throw e
        }
        Log.d(TAG,"*** Inserted new Upload Measurement record: $insertedMeasurement")

        try {
            insertedMeasurement = runBlocking {
                networkMeasurementDatasource.insertMeasurement(downloadMeasurement)
            }
        } catch (e: Exception) {
            Log.d(TAG, "Error inserting measurement record", e)
            throw e
        }
        Log.d(TAG,"*** Inserted new Download Measurement record: $insertedMeasurement")
    }

    @Test
    @Throws(Exception::class)
    fun GetMeasurements() {
        var measurements: List<Measurement>?
        val gson = GsonBuilder().setPrettyPrinting().create()

        try {
            measurements = runBlocking {
                networkMeasurementDatasource.getMeasurements()
            }
        } catch (e: Exception) {
            Log.d(TAG, "Error inserting measurement record", e)
            throw e
        }

        Log.d(TAG, "*** Got all Measurements: ${measurements?.count()}")

        measurements?.forEach { measurement ->
            val jsonString = gson.toJson(measurement.asNetworkModel())
            Log.d(TAG, jsonString)
        }
    }

    @Test
    @Throws(Exception::class)
    fun GetMeasurementById() {
        var measurement: Measurement?
        val measurementId = "0f65833e-5c3f-4738-8005-8b294ccf074d"
        val gson = GsonBuilder().setPrettyPrinting().create()

        try {
            measurement = runBlocking {
                networkMeasurementDatasource.getMeasurementById(measurementId)
            }
        } catch (e: Exception) {
            Log.d(TAG, "Cannot find Measurement with id = $measurementId", e)
            throw e
        }

        if (measurement != null) {
            Log.d(TAG, "*** Found Measurement with id = $measurementId")
            val jsonString = gson.toJson(measurement.asNetworkModel())
            Log.d(TAG, jsonString)
        }
    }
}
