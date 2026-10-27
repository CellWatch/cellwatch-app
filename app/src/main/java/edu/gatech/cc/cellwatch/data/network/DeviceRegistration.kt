package edu.gatech.cc.cellwatch.data.network

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import java.security.SecureRandom

/** Where the phone keeps its device secret, and whether the server has confirmed it. */
interface DeviceCredentials {
    suspend fun secret(): String?
    suspend fun saveSecret(secret: String)
    suspend fun isRegistered(): Boolean
    suspend fun markRegistered()
}

/**
 * The secret this phone sends with every request, registering it with the server first if the
 * server hasn't confirmed it yet.
 *
 * The phone makes the secret and saves it before registering, so if the response is lost, the next
 * attempt sends the same secret and the server accepts it. The server used to make the secret and
 * send it back once; a phone that lost that response could never register again, so it could never
 * upload. A phone registered by an older version already has a secret, and confirms it once.
 */
suspend fun registeredDeviceSecret(
    credentials: DeviceCredentials,
    newSecret: () -> String = { newDeviceSecret() },
    register: suspend (secret: String) -> Unit,
): String {
    val secret = credentials.secret() ?: newSecret().also { credentials.saveSecret(it) }
    if (!credentials.isRegistered()) {
        register(secret)
        credentials.markRegistered()
    }
    return secret
}

/** 32 random bytes as 64 hex digits. */
fun newDeviceSecret(random: SecureRandom = SecureRandom()): String =
    ByteArray(32).also { random.nextBytes(it) }.joinToString("") { "%02x".format(it) }

/**
 * Calls register_device(device_id, device_secret). It succeeds for a new device, for the secret the
 * server already has, and for a device that never uploaded anything; otherwise it raises "already
 * registered".
 */
suspend fun requestDeviceRegistration(client: SupabaseClient, deviceId: String, secret: String) {
    client.postgrest.rpc("register_device", mapOf("device_id" to deviceId, "device_secret" to secret))
}
