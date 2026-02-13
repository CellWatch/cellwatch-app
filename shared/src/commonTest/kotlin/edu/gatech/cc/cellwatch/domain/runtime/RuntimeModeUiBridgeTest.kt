package edu.gatech.cc.cellwatch.domain.runtime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RuntimeModeUiBridgeTest {
    private val bridge = RuntimeModeUiBridge()

    @Test
    fun nextMsakMode_cyclesPublicStagingLocal() {
        assertEquals(RuntimeMsakMode.STAGING, bridge.nextMsakMode(RuntimeMsakMode.PUBLIC))
        assertEquals(RuntimeMsakMode.LOCAL, bridge.nextMsakMode(RuntimeMsakMode.STAGING))
        assertEquals(RuntimeMsakMode.PUBLIC, bridge.nextMsakMode(RuntimeMsakMode.LOCAL))
    }

    @Test
    fun nextSupabaseMode_cyclesLocalTestingLive() {
        assertEquals(RuntimeSupabaseMode.TESTING, bridge.nextSupabaseMode(RuntimeSupabaseMode.LOCAL))
        assertEquals(RuntimeSupabaseMode.LIVE, bridge.nextSupabaseMode(RuntimeSupabaseMode.TESTING))
        assertEquals(RuntimeSupabaseMode.LOCAL, bridge.nextSupabaseMode(RuntimeSupabaseMode.LIVE))
    }

    @Test
    fun resolve_success_forLocalModeWithStrictValues() {
        val resolution = bridge.resolve(
            RuntimeProfileConfig(
                msakMode = RuntimeMsakMode.LOCAL,
                supabaseMode = RuntimeSupabaseMode.LOCAL,
                strictSupabaseConfig = true,
                allowRemoteSupabase = false,
                localMsakHost = "127.0.0.1:8080",
                localSupabaseUrl = "http://127.0.0.1:54321",
                localSupabaseApiKey = "local-api-key",
            ),
        )

        assertNull(resolution.errorMessage)
        val snapshot = resolution.snapshot
        assertNotNull(snapshot)
        assertEquals(RuntimeMsakMode.LOCAL, snapshot.msakMode)
        assertEquals(RuntimeSupabaseMode.LOCAL, snapshot.supabaseMode)
    }

    @Test
    fun resolve_returnsError_whenStrictLocalSupabaseMissing() {
        val resolution = bridge.resolve(
            RuntimeProfileConfig(
                msakMode = RuntimeMsakMode.LOCAL,
                supabaseMode = RuntimeSupabaseMode.LOCAL,
                strictSupabaseConfig = true,
                allowRemoteSupabase = false,
                localMsakHost = "127.0.0.1:8080",
                localSupabaseUrl = null,
                localSupabaseApiKey = null,
            ),
        )

        assertEquals(null, resolution.snapshot)
        assertTrue(resolution.errorMessage?.contains("SUPABASE_LOCAL_URL") == true)
    }
}
