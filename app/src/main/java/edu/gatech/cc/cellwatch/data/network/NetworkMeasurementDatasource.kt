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
import io.github.jan.supabase.SupabaseClientBuilder
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.rpc
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.api.createClientPlugin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

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
        // Only allow one attempt to create a client at a time, so two threads don't each make and
        // register a secret. Once the lock is acquired, do a second check for a non-null _client
        // just in case another thread set it while we were waiting.
        return _client ?: clientLock.withLock {
            var client = _client
            if (client != null) {
                return client
            }

            val deviceId = localDataStore.getDeviceId.first()
            val deviceSecret = registeredDeviceSecret(credentials) { registerDevice(deviceId, it) }

            client = createSupabaseClient(
                supabaseUrl = supabaseUrl,
                supabaseKey = supabaseApiKey
            ) {
                install(Postgrest)
                allowSlowRequests()

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

    private val credentials = object : DeviceCredentials {
        override suspend fun secret() = localDataStore.getDeviceSecret.first()
        override suspend fun saveSecret(secret: String) = localDataStore.saveDeviceSecret(secret)
        override suspend fun isRegistered() = localDataStore.getDeviceRegistered.first()
        override suspend fun markRegistered() = localDataStore.saveDeviceRegistered(true)
    }

    private suspend fun registerDevice(id: String, secret: String) {
        try {
            val client = createSupabaseClient(supabaseUrl, supabaseApiKey) {
                install(Postgrest)
                allowSlowRequests()
            }
            requestDeviceRegistration(client, id, secret)
            Log.i(TAG, "device registered with id $id")
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

/**
 * How long a Supabase request may take, connecting included. supabase-kt gives up after 10 s,
 * which uploads over a weak signal or to a slow server often need more than, so the same records
 * failed and were resent run after run. A minute leaves room for both, while a request that has
 * stalled still frees its upload slot well within WorkManager's 10 minute limit on a run.
 */
private val REQUEST_TIMEOUT = 60.seconds

internal fun SupabaseClientBuilder.allowSlowRequests(timeout: Duration = REQUEST_TIMEOUT) {
    requestTimeout = timeout
    httpConfig {
        // Ktor's CIO engine otherwise gives up connecting after 5 s. Ktor merges this with the
        // HttpTimeout settings supabase-kt installs, so the request timeout above still applies.
        install(HttpTimeout) { connectTimeoutMillis = timeout.inWholeMilliseconds }
    }
}
