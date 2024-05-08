package edu.gatech.cc.cellwatch.data.network

import androidx.annotation.WorkerThread
import edu.gatech.cc.cellwatch.BuildConfig
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
import io.github.jan.supabase.postgrest.rpc
import java.io.IOException

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
            supabaseUrl = supabaseUrl,
            supabaseKey = supabaseApiKey
        ) {
            install(Postgrest)
        }

    private val measurementTable = supabaseClient.postgrest["measurements"]
    private val fccSubmissionTable = supabaseClient.postgrest["fcc_submissions"]

    @WorkerThread
    suspend fun insertFccSubmission(fccSubmission: FccSubmission): FccSubmission {
        return withErrorHandling {
            fccSubmissionTable.insert(fccSubmission.asNetworkModel())
                .decodeSingle<NetworkFccSubmission>().asExternalModel()
        }
    }

    /**
     * Insert a measurement record and any associated data and locations
     */
    @WorkerThread
    suspend fun insertMeasurement(measurement: Measurement): Measurement {
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

        return withErrorHandling {
            supabaseClient.postgrest.rpc("insert_measurement", networkMeasurementData)
                .decodeAs<NetworkMeasurement>().asExternalModel()
        }
    }

    @WorkerThread
    suspend fun getMeasurementById(measurementId: String): Measurement {
        return withErrorHandling {
            measurementTable.select(
                columns = Columns.raw("""*,upload_download_data(*),latency_data(*),locations(*),cells(*)""")
            ) {
                Measurement::id eq measurementId
            }.decodeSingleOrNull<NetworkMeasurement>()?.asExternalModel() ?: throw NotFoundError()
        }
    }

    private suspend fun <T>withErrorHandling(fn: suspend () -> T): T {
        try {
            return fn()
        } catch (e: Exception) {
            when (e) {
                is IOException, is HttpRequestException -> {
                    throw NetworkError(e)
                }

                else -> if (
                    e is RestException
                    && e.error.startsWith("duplicate key value violates unique constraint")
                ) {
                    throw DuplicateKeyError(e)
                } else {
                    throw e
                }
            }
        }
    }

    class NetworkError(cause: Exception? = null): IOException(cause)
    class DuplicateKeyError(cause: Exception? = null): Exception(cause)
    class NotFoundError(cause: Exception? = null): Exception(cause)
}
