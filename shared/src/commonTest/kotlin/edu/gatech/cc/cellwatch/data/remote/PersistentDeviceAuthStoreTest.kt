package edu.gatech.cc.cellwatch.data.remote

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
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
        val store = PersistentDeviceAuthStore(storage)

        val id = store.getDeviceId()
        assertTrue(id.isNotBlank())
        assertNull(store.getDeviceSecret())
    }

    @Test
    fun theIdHandedOutBeforeRegistrationIsTheOneSavedWithTheSecret() = runBlocking {
        // getDeviceId() runs first and the caller registers THAT id, so the
        // secret must be stored against it - not against a freshly minted one.
        val storage = FakeStorage()
        val store = PersistentDeviceAuthStore(storage)

        val registeredId = store.getDeviceId()
        store.saveDeviceSecret("secret-from-server")

        assertEquals(registeredId, store.getDeviceId())
        assertEquals("secret-from-server", store.getDeviceSecret())
    }

    @Test
    fun aStoredPairIsReusedOnTheNextLaunch() = runBlocking {
        val storage = FakeStorage()
        val first = PersistentDeviceAuthStore(storage)
        val originalId = first.getDeviceId()
        first.saveDeviceSecret("s1")

        val next = PersistentDeviceAuthStore(storage)
        assertEquals(originalId, next.getDeviceId())
        assertEquals("s1", next.getDeviceSecret())
    }

    @Test
    fun anIdWithoutItsSecretIsDiscardedRatherThanReused() = runBlocking {
        // The bug this class exists for: reusing such an id means calling
        // register_device on an id the server already knows, which fails
        // permanently because the secret cannot be reissued.
        val storage = FakeStorage("""{"deviceId":"already-registered-id","deviceSecret":""}""")
        val store = PersistentDeviceAuthStore(storage)

        assertNotEquals("already-registered-id", store.getDeviceId())
        assertNull(store.getDeviceSecret())
        assertTrue(storage.clears > 0, "the unusable record should be cleared")
    }

    @Test
    fun anUnreadableRecordMintsAFreshPairInsteadOfThrowing() = runBlocking {
        val storage = FakeStorage("not json at all")
        val store = PersistentDeviceAuthStore(storage)

        assertTrue(store.getDeviceId().isNotBlank())
        assertNull(store.getDeviceSecret())
    }

    @Test
    fun theIdIsStableWithinALaunchBeforeAnythingIsPersisted() = runBlocking {
        val storage = FakeStorage()
        val store = PersistentDeviceAuthStore(storage)

        assertEquals(store.getDeviceId(), store.getDeviceId())
    }
}
