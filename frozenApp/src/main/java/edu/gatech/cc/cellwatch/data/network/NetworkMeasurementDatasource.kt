package edu.gatech.cc.cellwatch.data.network

import androidx.annotation.WorkerThread
import edu.gatech.cc.cellwatch.BuildConfig
import edu.gatech.cc.cellwatch.core.util.Log
import edu.gatech.cc.cellwatch.data.datastore.LocalDataStore
import edu.gatech.cc.cellwatch.data.model.FccSubmission
import edu.gatech.cc.cellwatch.data.model.Measurement
import edu.gatech.cc.cellwatch.data.model.asNetworkModel
import edu.gatech.cc.cellwatch.data.network.model.NetworkFccSubmission
import edu.gatech.cc.cellwatch.data.network.model.NetworkMeasurement
import edu.gatech.cc.cellwatch.data.network.model.NetworkMeasurementWithData
import edu.gatech.cc.cellwatch.data.network.model.asExternalModel
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.rpc
import io.ktor.client.plugins.api.createClientPlugin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

/**
 * NetworkMeasurementDatasource is a singleton object representing a network data source with
 * member functions to read/write [NetworkMeasurement] records to the remote Supabase database.
 * NetworkMeasurementDatasource is used by MeasurementRepository to synchronize locally stored
 * [Measurement] records to Supabase.
 */

class NetworkMeasurementDatasource(private val localDataStore: LocalDataStore) {
    private val TAG = this::class.simpleName

    private val supabaseUrl = BuildConfig.SUPABASE_URL
    private val supabaseApiKey = BuildConfig.SUPABASE_API_KEY

    private var _client: SupabaseClient? = null // use getClient(), not this field!
    private val clientLock = Mutex()
    private suspend fun getClient(): SupabaseClient {
        // Only allow one attempt to create a client at a time to avoid registration errors (i.e.,
        // one thread registers and then another also tries to register but gets an error because
        // the same id was already registered by the first thread). Once the lock is acquired, do a
        // second check for a non-null _client just in case another thread set it while we were
        // waiting.
        return _client ?: clientLock.withLock {
            var client = _client
            if (client != null) {
                return client
            }

            val deviceId = localDataStore.getDeviceId.first()
            val deviceSecret = localDataStore.getDeviceSecret.first() ?: registerDevice(deviceId)

            client = createSupabaseClient(
                supabaseUrl = supabaseUrl,
                supabaseKey = supabaseApiKey
            ) {
                install(Postgrest)

                httpConfig {
                    install(createClientPlugin("DeviceHeaderPlugin") {
                        onRequest { request, _ ->
                            request.headers.append("X-Device-ID", deviceId)
                            request.headers.append("X-Device-Secret", deviceSecret)
                        }
                    })
                }
            }

            _client = client
            return client
        }
    }

    private suspend fun registerDevice(id: String): String {
        try {
            val client = createSupabaseClient(supabaseUrl, supabaseApiKey) { install(Postgrest) }
            val secret = client.postgrest
                .rpc("register_device", mapOf("device_id" to id))
                .decodeAs<String>()
            localDataStore.saveDeviceSecret(secret)
            Log.i(TAG, "device registered with id $id")
            return secret
        } catch (e: Exception) {
            Log.i(TAG, "device registration failed for id $id", e)
            throw e
        }
    }

    @WorkerThread
    suspend fun insertFccSubmission(fccSubmission: FccSubmission): FccSubmission {
        return withErrorHandling {
            getClient().postgrest["fcc_submissions"].insert(fccSubmission.asNetworkModel())
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
            getClient().postgrest.rpc("insert_measurement", networkMeasurementData)
                .decodeAs<NetworkMeasurement>().asExternalModel()
        }
    }

    @WorkerThread
    suspend fun getMeasurementById(measurementId: String): Measurement {
        return withErrorHandling {
            getClient().postgrest["measurements"].select(
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
