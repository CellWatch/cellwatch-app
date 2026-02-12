package edu.gatech.cc.cellwatch.domain.runtime

import edu.gatech.cc.cellwatch.data.sync.SyncTransportTarget
import edu.gatech.cc.cellwatch.domain.fcc.MsakLocateEnvironment
import kotlin.test.Test
import kotlin.test.assertEquals

class RuntimeSyncMsakProfilesTest {
    @Test
    fun publicMsakLocalSupabase_profileIsPinnedToProdMsakAndLocalSupabase() {
        val profile = RuntimeSyncMsakProfiles.publicMsakLocalSupabase(
            localSupabaseUrl = "http://10.0.2.2:54321",
            localSupabaseApiKey = "local-key",
            userAgent = "profile-test-agent",
        )

        assertEquals(MsakLocateEnvironment.PROD, profile.msakConfig.environment)
        assertEquals("profile-test-agent", profile.msakConfig.userAgent)
        assertEquals(SyncTransportTarget.LOCAL, profile.syncTarget)
        assertEquals("http://127.0.0.1:54321", profile.resolveSyncSupabaseConfig().url)
        assertEquals("local-key", profile.resolveSyncSupabaseConfig().apiKey)
    }

    @Test
    fun bridge_resolvesPublicMsakLocalSupabaseSnapshot() {
        val snapshot = RuntimeSyncMsakProfileBridge().resolvePublicMsakLocalSupabase(
            localSupabaseUrl = "http://127.0.0.1:54321",
            localSupabaseApiKey = "bridge-key",
            userAgent = "bridge-agent",
        )

        assertEquals(MsakLocateEnvironment.PROD, snapshot.msakEnvironment)
        assertEquals("bridge-agent", snapshot.msakUserAgent)
        assertEquals("http://127.0.0.1:54321", snapshot.supabaseUrl)
        assertEquals("bridge-key", snapshot.supabaseApiKey)
    }
}
