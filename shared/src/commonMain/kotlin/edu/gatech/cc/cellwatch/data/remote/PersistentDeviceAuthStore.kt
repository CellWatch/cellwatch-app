package edu.gatech.cc.cellwatch.data.remote

import com.benasher44.uuid.uuid4
import edu.gatech.cc.cellwatch.core.util.SecureEncryptor
import edu.gatech.cc.cellwatch.core.util.SharedLog
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Where a device credential is kept between launches.
 *
 * Deliberately dumb - one opaque string - so the pairing rule below lives in
 * one testable place rather than being reimplemented per platform.
 */
interface DeviceCredentialStorage {
    fun read(): String?
    fun write(value: String)
    fun clear()
}

@Serializable
internal data class StoredDeviceCredential(
    val deviceId: String,
    val deviceSecret: String,
)

/**
 * Remembers the device credential, and treats the id and secret as one unit.
 *
 * `register_device` returns the secret exactly once and stores only a bcrypt
 * hash, so a secret that is not kept is unrecoverable - and the server refuses
 * to re-register an id it already knows. An id without its secret is therefore
 * dead weight: it can never authenticate again.
 *
 * The previous stores kept nothing at all ([SupabaseMeasurementSyncRemoteDataSource]
 * was handed an in-memory store), while the id came from the OS -
 * identifierForVendor on iOS, ANDROID_ID on Android - which is stable for free.
 * So the id was never missing and the secret never present: every launch
 * re-registered an already-registered id, the call failed, and sync uploaded
 * nothing. That could not self-correct, because "make a new id if we have none"
 * never fired.
 *
 * Hence: an id is only reused when its secret was stored alongside it. Anything
 * else - first launch, reinstall, cleared storage, a half-written record -
 * mints a fresh pair, which always registers cleanly. FCC permits this: the BDC
 * spec defines device_id as a "Unique device **or application installation**
 * identifier", so an install-scoped UUID is valid, and it keeps an OS-level
 * device identifier off the wire.
 */
class PersistentDeviceAuthStore(
    private val storage: DeviceCredentialStorage,
    private val newDeviceId: () -> String = { uuid4().toString() },
    /**
     * Encryption at rest, matching what the pre-KMP app did (frozenApp
     * encrypted the secret before writing it to DataStore). The storage is
     * already app-private, so this is defence in depth rather than the only
     * barrier.
     *
     * A decryption failure needs no special handling: [loadValid] treats an
     * unreadable record as absent, which mints a fresh credential - already the
     * correct recovery, since a secret we cannot read is a secret we have lost.
     */
    private val encrypt: (String) -> String = { SecureEncryptor.encrypt(it) },
    private val decrypt: (String) -> String = { SecureEncryptor.decrypt(it) },
) : DeviceAuthStore {

    private var pendingDeviceId: String? = null

    override suspend fun getDeviceId(): String {
        loadValid()?.let { return it.deviceId }
        return pendingDeviceId ?: newDeviceId().also { pendingDeviceId = it }
    }

    override suspend fun getDeviceSecret(): String? = loadValid()?.deviceSecret

    override suspend fun saveDeviceSecret(secret: String) {
        val id = loadValid()?.deviceId ?: pendingDeviceId ?: newDeviceId().also { pendingDeviceId = it }
        val plaintext = json.encodeToString(StoredDeviceCredential.serializer(), StoredDeviceCredential(id, secret))
        // Encryption is defence in depth over already app-private storage, so
        // losing it must not cost us the credential: a store that throws here
        // would re-register on every launch, which is the exact failure this
        // class was written to end.
        val payload = runCatching { encrypt(plaintext) }.getOrElse { error ->
            SharedLog.w(LOG_TAG, "credential encryption unavailable; storing unencrypted", error)
            plaintext
        }
        storage.write(payload)
        pendingDeviceId = null
    }

    /**
     * A record counts only when BOTH halves survived. A half-written or
     * unreadable record is discarded rather than half-trusted - keeping the id
     * from it is exactly the state that cannot recover.
     */
    private fun loadValid(): StoredDeviceCredential? {
        val raw = storage.read()?.takeIf { it.isNotBlank() } ?: return null
        // Accept an unencrypted record too: credentials written before
        // encryption was added must keep working, and a plaintext fallback
        // above has to be readable afterwards.
        val parsed = runCatching { json.decodeFromString<StoredDeviceCredential>(decrypt(raw)) }.getOrNull()
            ?: runCatching { json.decodeFromString<StoredDeviceCredential>(raw) }.getOrNull()
        if (parsed == null || parsed.deviceId.isBlank() || parsed.deviceSecret.isBlank()) {
            storage.clear()
            return null
        }
        return parsed
    }

    private companion object {
        const val LOG_TAG = "DeviceAuth"
        val json = Json { ignoreUnknownKeys = true }
    }
}
