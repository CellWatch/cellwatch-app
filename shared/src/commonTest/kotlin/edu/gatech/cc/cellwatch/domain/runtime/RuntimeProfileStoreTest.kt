package edu.gatech.cc.cellwatch.domain.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class RuntimeProfileStoreTest {
    @Test
    fun inMemoryStore_roundTripsAndClears() {
        val store = InMemoryRuntimeProfileStore()
        assertNull(store.load())

        val config = RuntimeProfileConfig(
            msakMode = RuntimeMsakMode.LOCAL,
            supabaseMode = RuntimeSupabaseMode.LOCAL,
            localSupabaseUrl = "http://127.0.0.1:54321",
            localSupabaseApiKey = "local-key",
            localMsakHost = "127.0.0.1:8080",
            userAgent = "runtime-store-test",
        )
        store.save(config)

        val loaded = store.load()
        requireNotNull(loaded)
        assertEquals(RuntimeMsakMode.LOCAL, loaded.msakMode)
        assertEquals("http://127.0.0.1:54321", loaded.localSupabaseUrl)
        assertEquals("local-key", loaded.localSupabaseApiKey)

        store.clear()
        assertNull(store.load())
    }

    @Test
    fun resolver_resolvesSnapshotFromStoreFallback() {
        val store = InMemoryRuntimeProfileStore()
        val fallback = RuntimeProfileConfig(
            msakMode = RuntimeMsakMode.PUBLIC,
            supabaseMode = RuntimeSupabaseMode.LOCAL,
            strictSupabaseConfig = true,
            localSupabaseUrl = "http://127.0.0.1:54321",
            localSupabaseApiKey = "fallback-key",
            userAgent = "runtime-fallback-test",
        )

        val snapshot = RuntimeProfileResolver.resolveSnapshot(store, fallback)
        assertEquals(RuntimeMsakMode.PUBLIC, snapshot.msakMode)
        assertEquals("http://127.0.0.1:54321", snapshot.supabaseUrl)
        assertEquals("fallback-key", snapshot.supabaseApiKey)
    }

    @Test
    fun resolver_strictMissingLocalConfig_throws() {
        val config = RuntimeProfileConfig(
            msakMode = RuntimeMsakMode.PUBLIC,
            supabaseMode = RuntimeSupabaseMode.LOCAL,
            strictSupabaseConfig = true,
            localSupabaseUrl = null,
            localSupabaseApiKey = null,
        )

        assertFailsWith<IllegalStateException> {
            RuntimeProfileResolver.resolveSnapshot(config)
        }
    }
}
