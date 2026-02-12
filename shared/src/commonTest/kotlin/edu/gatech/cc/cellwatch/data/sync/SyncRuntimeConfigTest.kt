package edu.gatech.cc.cellwatch.data.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SyncRuntimeConfigTest {

    @Test
    fun localDefaults_useLoopbackAndDefaultKey() {
        val cfg = SyncRuntimeConfigFactory.fromRaw()

        val resolved = cfg.resolve(SyncTransportTarget.LOCAL)

        assertEquals("http://127.0.0.1:54321", resolved.url)
        assertEquals(true, resolved.apiKey.startsWith("eyJ"))
    }

    @Test
    fun localUrl_normalizesAndroidLoopback() {
        val cfg = SyncRuntimeConfigFactory.fromRaw(
            localUrl = "http://10.0.2.2:54321",
            localApiKey = "local-key",
        )

        val resolved = cfg.resolve(SyncTransportTarget.LOCAL)

        assertEquals("http://127.0.0.1:54321", resolved.url)
        assertEquals("local-key", resolved.apiKey)
    }

    @Test
    fun remote_isBlockedByDefault() {
        val cfg = SyncRuntimeConfigFactory.fromRaw(
            remoteUrl = "https://example.supabase.co",
            remoteApiKey = "remote-key",
        )

        assertFailsWith<IllegalStateException> {
            cfg.resolve(SyncTransportTarget.REMOTE)
        }
    }

    @Test
    fun bridge_buildsProfileAndResolvesLocalConfig() {
        val cfg = SyncRuntimeProfileBridge.resolveSupabaseConfig(
            localUrl = "http://10.0.2.2:54321",
            localApiKey = "local-key",
            useRemote = false,
        )

        assertEquals("http://127.0.0.1:54321", cfg.url)
        assertEquals("local-key", cfg.apiKey)
    }
}
