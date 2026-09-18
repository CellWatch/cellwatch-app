package edu.gatech.cc.cellwatch.data.remote

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class FakeStorage(var value: String? = null) : DeviceCredentialStorage {
    var clears = 0
    override fun read(): String? = value
    override fun write(value: String) { this.value = value }
    override fun clear() { value = null; clears++ }
}

class PersistentDeviceAuthStoreTest {

    @Test
    fun firstLaunchMintsAnIdAndHasNoSecretYet() = runBlocking {
        val storage = FakeStorage()
        val store = PersistentDeviceAuthStore(storage, encrypt = { it }, decrypt = { it })

        val id = store.getDeviceId()
        assertTrue(id.isNotBlank())
        assertNull(store.getDeviceSecret())
    }

    @Test
    fun theIdHandedOutBeforeRegistrationIsTheOneSavedWithTheSecret() = runBlocking {
        // getDeviceId() runs first and the caller registers THAT id, so the
        // secret must be stored against it - not against a freshly minted one.
        val storage = FakeStorage()
        val store = PersistentDeviceAuthStore(storage, encrypt = { it }, decrypt = { it })

        val registeredId = store.getDeviceId()
        store.saveDeviceSecret("secret-from-server")

        assertEquals(registeredId, store.getDeviceId())
        assertEquals("secret-from-server", store.getDeviceSecret())
    }

    @Test
    fun aStoredPairIsReusedOnTheNextLaunch() = runBlocking {
        val storage = FakeStorage()
        val first = PersistentDeviceAuthStore(storage, encrypt = { it }, decrypt = { it })
        val originalId = first.getDeviceId()
        first.saveDeviceSecret("s1")

        val next = PersistentDeviceAuthStore(storage, encrypt = { it }, decrypt = { it })
        assertEquals(originalId, next.getDeviceId())
        assertEquals("s1", next.getDeviceSecret())
    }

    @Test
    fun anIdWithoutItsSecretIsDiscardedRatherThanReused() = runBlocking {
        // The bug this class exists for: reusing such an id means calling
        // register_device on an id the server already knows, which fails
        // permanently because the secret cannot be reissued.
        val storage = FakeStorage("""{"deviceId":"already-registered-id","deviceSecret":""}""")
        val store = PersistentDeviceAuthStore(storage, encrypt = { it }, decrypt = { it })

        assertNotEquals("already-registered-id", store.getDeviceId())
        assertNull(store.getDeviceSecret())
        assertTrue(storage.clears > 0, "the unusable record should be cleared")
    }

    @Test
    fun anUnreadableRecordMintsAFreshPairInsteadOfThrowing() = runBlocking {
        val storage = FakeStorage("not json at all")
        val store = PersistentDeviceAuthStore(storage, encrypt = { it }, decrypt = { it })

        assertTrue(store.getDeviceId().isNotBlank())
        assertNull(store.getDeviceSecret())
    }

    @Test
    fun theIdIsStableWithinALaunchBeforeAnythingIsPersisted() = runBlocking {
        val storage = FakeStorage()
        val store = PersistentDeviceAuthStore(storage, encrypt = { it }, decrypt = { it })

        assertEquals(store.getDeviceId(), store.getDeviceId())
    }

    @Test
    fun theStoredRecordIsEncryptedAndRoundTrips() = runBlocking {
        val storage = FakeStorage()
        val store = PersistentDeviceAuthStore(
            storage,
            // Reversal, not a prefix: the point of the assertion below is that
            // the plaintext must not be recoverable by reading storage.
            encrypt = { "enc:" + it.reversed() },
            decrypt = { it.removePrefix("enc:").reversed() },
        )

        val id = store.getDeviceId()
        store.saveDeviceSecret("top-secret")

        assertTrue(storage.value!!.startsWith("enc:"), "the record must not be written in the clear")
        assertFalse(storage.value!!.contains("top-secret"), "the secret must not be readable in storage")

        val next = PersistentDeviceAuthStore(
            storage,
            // Reversal, not a prefix: the point of the assertion below is that
            // the plaintext must not be recoverable by reading storage.
            encrypt = { "enc:" + it.reversed() },
            decrypt = { it.removePrefix("enc:").reversed() },
        )
        assertEquals(id, next.getDeviceId())
        assertEquals("top-secret", next.getDeviceSecret())
    }

    @Test
    fun aRecordWrittenBeforeEncryptionExistedIsStillReadable() = runBlocking {
        // Credentials already on devices are plaintext. Rejecting them would
        // orphan a registered device id for no reason.
        val storage = FakeStorage("""{"deviceId":"legacy-id","deviceSecret":"legacy-secret"}""")
        val store = PersistentDeviceAuthStore(storage, encrypt = { it }, decrypt = { error("not encrypted") })

        assertEquals("legacy-id", store.getDeviceId())
        assertEquals("legacy-secret", store.getDeviceSecret())
    }

    @Test
    fun encryptionFailingDoesNotCostUsTheCredential() = runBlocking {
        // A store that threw here would re-register every launch, which is the
        // exact failure this class exists to prevent.
        val storage = FakeStorage()
        val store = PersistentDeviceAuthStore(
            storage,
            encrypt = { error("keystore unavailable") },
            decrypt = { it },
        )

        val id = store.getDeviceId()
        store.saveDeviceSecret("s")

        assertEquals(id, store.getDeviceId())
        assertEquals("s", store.getDeviceSecret())
    }
}
