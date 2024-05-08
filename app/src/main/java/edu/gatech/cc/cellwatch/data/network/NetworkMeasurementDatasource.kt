package edu.gatech.cc.cellwatch.data.network

import androidx.annotation.WorkerThread
import edu.gatech.cc.cellwatch.BuildConfig
import edu.gatech.cc.cellwatch.CellWatchApp
import edu.gatech.cc.cellwatch.core.util.Log
import edu.gatech.cc.cellwatch.data.model.FccSubmission
import edu.gatech.cc.cellwatch.data.model.Measurement
import edu.gatech.cc.cellwatch.data.model.asNetworkModel
import edu.gatech.cc.cellwatch.data.network.model.NetworkFccSubmission
import edu.gatech.cc.cellwatch.data.network.model.NetworkMeasurement
import edu.gatech.cc.cellwatch.data.network.model.NetworkMeasurementWithData
import edu.gatech.cc.cellwatch.data.network.model.asExternalModel
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.PostgrestResult
import io.github.jan.supabase.postgrest.rpc
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

/**
 * NetworkMeasurementDatasource is a singleton object representing a network data source with
 * member functions to read/write [NetworkMeasurement] records to the remote Supabase database.
 * NetworkMeasurementDatasource is used by MeasurementRepository to synchronize locally stored
 * [Measurement] records to Supabase.
 */

object NetworkMeasurementDatasource {
    private val TAG = this::class.simpleName

    private const val supabaseUrl = BuildConfig.SUPABASE_URL
    private const val supabaseApiKey = BuildConfig.SUPABASE_API_KEY

    private val supabaseClient = createSupabaseClient(
            supabaseUrl = supabaseUrl, //"https://xepxxvpbexkyxrwtrgqv.supabase.co",
            supabaseKey = supabaseApiKey
        ) {
            install(Postgrest)
        }

    private val measurementTable = supabaseClient.postgrest["measurements"]
    private val fccSubmissionTable = supabaseClient.postgrest["fcc_submissions"]

    @WorkerThread
    suspend fun insertFccSubmissions(fccSubmissions: List<FccSubmission>): List<FccSubmission>? {
        var insertedFccSubmissions: List<FccSubmission>? = null

        if (fccSubmissions.isNotEmpty()) {
            Log.d(TAG, "insertFccSubmissions: Attempting to upload ${fccSubmissions.size} fccSubmissions")
            // Move network IO off the Main thread
            withContext(Dispatchers.IO) {
                try {
                    insertedFccSubmissions = fccSubmissions.map { fccSubmission ->
                        Log.d(TAG, "insertFccSubmissions: $fccSubmission")
                        insertFccSubmission(fccSubmission)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error in insertFccSubmissions: ${e.message}")
                    throw e
                }
            }
        }
        
        return insertedFccSubmissions
    }
    
    @WorkerThread
    suspend fun insertFccSubmission(fccSubmission: FccSubmission): FccSubmission {
        val insertedFccSubmission: FccSubmission?

        Log.d(TAG, "Attempting to insert FccSubmission ${fccSubmission.id} to Supabase API at $supabaseUrl")
        try {
            insertedFccSubmission =
                fccSubmissionTable.insert(fccSubmission.asNetworkModel()).decodeSingle<NetworkFccSubmission>().asExternalModel()

            Log.d(
                TAG,
                "*** Inserted new FccSubmission record: $insertedFccSubmission"
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

        return insertedFccSubmission
    }

    /**
     * Insert a measurement record and any associated data and locations
     */
    @WorkerThread
    private suspend fun insertMeasurementTransaction(measurement: Measurement): Measurement {
        val insertedMeasurement: Measurement?

        Log.d(TAG, "Attempting to insert measurement ${measurement.id} to Supabase API at $supabaseUrl")

        if (measurement.cells == null) {
            Log.e(TAG, "Error in insertMeasurementTransaction: measurement.cells is null!!!")
        }

        val networkMeasurementData = NetworkMeasurementWithData(
            measurement.asNetworkModel(),
            measurement.uploadDownloadData?.asNetworkModel(),
            measurement.latencyData?.asNetworkModel(),
            measurement.locations?.map { location -> location.asNetworkModel() },
            measurement.cells?.map { cell -> cell.asNetworkModel() }
        )

        try {
            insertedMeasurement =
                supabaseClient.postgrest.rpc("insert_measurement", networkMeasurementData).decodeAs<NetworkMeasurement>().asExternalModel()

            // Update synchronized status in local Room database
            insertedMeasurement.uploadTime = Clock.System.now()
            CellWatchApp.measurementRepository.updateMeasurement(insertedMeasurement)

            Log.d(
                TAG,
                "*** Inserted new Measurement record as transaction: $insertedMeasurement"
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

        return insertedMeasurement
    }

    @WorkerThread
    suspend fun uploadMeasurements(measurements: List<Measurement>): List<NetworkRestResult> = coroutineScope {
        val deferredResults = measurements.map { measurement ->
            async(Dispatchers.IO) {
                try {
                    Log.d(TAG, "insertMeasurement: $measurement")
                    NetworkRestResult(insertMeasurementTransaction(measurement), null)
                } catch (e: RestException) {
                    Log.e(TAG, "RestException: ${e.error}")
                    // Don't fail on Rest Exceptions
                    NetworkRestResult(measurement, e)
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
        }

        deferredResults.awaitAll()
    }

    @WorkerThread
    suspend fun getMeasurementById(measurementId: String): Measurement? {
        var result: PostgrestResult

        // Move network IO off the Main thread
        withContext(Dispatchers.IO) {
            try {
                result = measurementTable.select(
                    columns = Columns.raw("""*,upload_download_data(*),latency_data(*),locations(*),cells(*)""")
                ) {
                    Measurement::id eq measurementId
                }

                Log.d(TAG, "getMeasurementById: PostgrestResult = ${result.body}")
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

        // return Measurement or null if not found
        return result.decodeSingleOrNull<NetworkMeasurement>()?.asExternalModel()
    }
}
