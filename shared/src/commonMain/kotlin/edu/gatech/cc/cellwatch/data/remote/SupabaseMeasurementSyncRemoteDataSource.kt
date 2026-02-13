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
            getClient().postgrest
                .rpc("insert_measurement", measurement.toNetworkWithData())
                .decodeAs<JsonElement>()
            measurement
        }

    override suspend fun getMeasurementById(id: String): Measurement =
        withErrorHandling {
            getClient().postgrest["measurements"].select {
                Measurement::id eq id
            }.decodeSingleOrNull<NetworkMeasurement>()?.toDomain() ?: throw NotFoundError()
        }

    override suspend fun insertFccSubmission(submission: FccSubmission): FccSubmission =
        withErrorHandling {
            getClient().postgrest["fcc_submissions"]
                .insert(submission.toNetwork())
                .decodeAs<JsonElement>()
            submission
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
