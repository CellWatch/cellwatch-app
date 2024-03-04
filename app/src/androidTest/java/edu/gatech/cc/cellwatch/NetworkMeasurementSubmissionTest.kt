package edu.gatech.cc.cellwatch

import edu.gatech.cc.cellwatch.core.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import edu.gatech.cc.cellwatch.data.local.model.FccSubmissionEntity
import edu.gatech.cc.cellwatch.data.model.ChallengeData
import edu.gatech.cc.cellwatch.data.model.FccSubmission
import edu.gatech.cc.cellwatch.data.model.Measurement
import edu.gatech.cc.cellwatch.data.model.asNetworkModel
import edu.gatech.cc.cellwatch.data.network.NetworkMeasurementDatasource
import edu.gatech.cc.cellwatch.data.network.model.NetworkFccSubmission
import edu.gatech.cc.cellwatch.data.network.model.NetworkMeasurement
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
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID


@RunWith(AndroidJUnit4::class)
class NetworkMeasurementSubmissionTest {
    private lateinit var networkMeasurementDatasource: NetworkMeasurementDatasource

    private lateinit var supabaseClient: SupabaseClient
    private lateinit var fccSubmissionTable: PostgrestBuilder
    private lateinit var measurementTable: PostgrestBuilder
    private lateinit var locationTable: PostgrestBuilder
    private lateinit var dataTable: PostgrestBuilder
    private lateinit var latencyTable: PostgrestBuilder

    companion object {
        const val TAG = "NetworkMeasurementSubmissionTest"
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

        fccSubmissionTable = supabaseClient.postgrest["fcc_submissions"]
        measurementTable = supabaseClient.postgrest["measurements"]
        locationTable = supabaseClient.postgrest["locations"]
        dataTable = supabaseClient.postgrest["upload_download_data"]
        latencyTable = supabaseClient.postgrest["latency_data"]
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
    @kotlin.jvm.Throws(Exception::class)
    fun InsertFccSubmission() {
        var insertedFccSubmission: FccSubmission?
        
        val groupId = UUID.randomUUID().toString()
        val fccSubmission = FccSubmission(
            id = groupId,
            contactName = "Scott Robertson",
            contactEmail = "sr19@gatech.edu",
            contactPhone = "404-226-2082",
            deviceTimestamp = Clock.System.now(),
            serverTimestamp = Clock.System.now(),
            sourceIp = "10.0.1.2",
            simCountryCode = "410",
            simNetworkCode = "310",
            netCountryCode = "410",
            netNetworkCode = "310",
            inVehicle = false,
            externalAntenna = false,
            submitted = false
        )

        try {
            var result: PostgrestResult
            insertedFccSubmission =
                runBlocking {
                    result = fccSubmissionTable.insert(fccSubmission.asNetworkModel())

                    result.decodeSingle<NetworkFccSubmission>().asExternalModel()
                }
            Log.d(
                TAG,
                "*** Inserted new Measurement record: $insertedFccSubmission"
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
    fun ChallengeDataInsert() {
        val deviceId = UUID.randomUUID().toString()
        val groupId = UUID.randomUUID().toString()
        val campaignId = UUID.randomUUID().toString()
        val sessionId = UUID.randomUUID().toString()

        val challengeData = ChallengeData(
            submissionCategory = "Consumer Challenge",
            contactName = "George Burdell",
            contactEmail = "gburdell@gatech.edu",
            contactPhone = "404-555-2000"
        )

        val fccSubmission = FccSubmission(
            id = groupId,
            contactName = challengeData.contactName,
            contactPhone = challengeData.contactPhone,
            contactEmail = challengeData.contactEmail,
            deviceTimestamp = Clock.System.now(),
            serverTimestamp = Clock.System.now(),
            sourceIp = "10.0.0.1",
            sourcePort = "3755",
            deviceImei = "12345678910",
            deviceTac = "12345678",
            simCountryCode = "IT",
            simNetworkCode = "IT",
            netCountryCode = "IT",
            netNetworkCode = "IT",
            inVehicle = false,
            externalAntenna = false
        )


    }
}
