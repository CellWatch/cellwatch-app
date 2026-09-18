package edu.gatech.cc.cellwatch.data.remote

import edu.gatech.cc.cellwatch.data.transport.NetworkMeasurement
import edu.gatech.cc.cellwatch.data.transport.toDomain
import edu.gatech.cc.cellwatch.data.transport.toNetwork
import edu.gatech.cc.cellwatch.data.transport.toNetworkWithData
import edu.gatech.cc.cellwatch.domain.model.FccSubmission
import edu.gatech.cc.cellwatch.domain.model.Measurement
import edu.gatech.cc.cellwatch.domain.sync.DuplicateKeyError
import edu.gatech.cc.cellwatch.domain.sync.MeasurementSyncRemoteDataSource
import edu.gatech.cc.cellwatch.domain.sync.NetworkError
import edu.gatech.cc.cellwatch.domain.sync.NotFoundError
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.exceptions.HttpRequestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.utils.io.errors.IOException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement

data class SupabaseConnectionConfig(
    val url: String,
    val apiKey: String,
)

interface DeviceAuthStore {
    suspend fun getDeviceId(): String
    suspend fun getDeviceSecret(): String?
    suspend fun saveDeviceSecret(secret: String)
}

class SupabaseMeasurementSyncRemoteDataSource(
    private val config: SupabaseConnectionConfig,
    private val deviceAuthStore: DeviceAuthStore,
) : MeasurementSyncRemoteDataSource {

    private var client: SupabaseClient? = null
    private val clientLock = Mutex()

    override suspend fun insertMeasurement(measurement: Measurement): Measurement =
        withErrorHandling {
            // Stamp ownership here rather than trusting the caller. RLS checks
            // `verified_device_id() = device_id` on insert (insert_measurement
            // is SECURITY INVOKER, so the policy applies), and the column is
            // nullable with no default - a row carrying null device_id compares
            // as NULL and is silently rejected. Deriving it from the same store
            // that authenticates the request keeps the two in step by
            // construction.
            val owned = measurement.copy(deviceId = deviceAuthStore.getDeviceId())
            getClient().postgrest
                .rpc("insert_measurement", owned.toNetworkWithData())
                .decodeAs<JsonElement>()
            owned
        }

    override suspend fun getMeasurementById(id: String): Measurement =
        withErrorHandling {
            getClient().postgrest["measurements"].select {
                Measurement::id eq id
            }.decodeSingleOrNull<NetworkMeasurement>()?.toDomain() ?: throw NotFoundError()
        }

    override suspend fun insertFccSubmission(submission: FccSubmission): FccSubmission =
        withErrorHandling {
            // Same ownership rule as measurements: "Allow inserting own FCC
            // submissions" checks verified_device_id() = device_id.
            val owned = submission.copy(deviceId = deviceAuthStore.getDeviceId())
            getClient().postgrest["fcc_submissions"]
                .insert(owned.toNetwork())
                .decodeAs<JsonElement>()
            owned
        }

    private suspend fun getClient(): SupabaseClient {
        return client ?: clientLock.withLock {
            val current = client
            if (current != null) return current

            val deviceId = deviceAuthStore.getDeviceId()
            val deviceSecret = deviceAuthStore.getDeviceSecret() ?: registerDevice(deviceId)

            val newClient = createSupabaseClient(
                supabaseUrl = config.url,
                supabaseKey = config.apiKey,
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
            client = newClient
            newClient
        }
    }

    private suspend fun registerDevice(deviceId: String): String {
        val bootstrapClient = createSupabaseClient(
            supabaseUrl = config.url,
            supabaseKey = config.apiKey,
        ) {
            install(Postgrest)
        }
        val secret = bootstrapClient.postgrest
            .rpc("register_device", mapOf("device_id" to deviceId))
            .decodeAs<String>()
        deviceAuthStore.saveDeviceSecret(secret)
        return secret
    }

    private suspend fun <T> withErrorHandling(block: suspend () -> T): T {
        try {
            return block()
        } catch (e: Exception) {
            when (e) {
                is IOException, is HttpRequestException -> throw NetworkError(e)
                is RestException -> {
                    if (e.error.startsWith("duplicate key value violates unique constraint")) {
                        throw DuplicateKeyError(e)
                    }
                    throw e
                }
                else -> throw e
            }
        }
    }
}
