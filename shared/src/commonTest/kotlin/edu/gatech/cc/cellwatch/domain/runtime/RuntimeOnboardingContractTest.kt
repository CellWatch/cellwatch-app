package edu.gatech.cc.cellwatch.domain.runtime

import kotlin.test.Test
import kotlin.test.assertEquals

class RuntimeOnboardingContractTest {
    @Test
    fun onboardingDraft_toRuntimeProfileConfig_preservesFields() {
        val draft = RuntimeOnboardingDraft(
            msakMode = RuntimeMsakMode.STAGING,
            supabaseMode = RuntimeSupabaseMode.TESTING,
            localSupabaseUrl = "http://127.0.0.1:54321",
            localSupabaseApiKey = "local-key",
            testingSupabaseUrl = "https://testing.example.com",
            testingSupabaseApiKey = "testing-key",
            liveSupabaseUrl = "https://live.example.com",
            liveSupabaseApiKey = "live-key",
            localMsakHost = "127.0.0.1:8080",
            localMsakSecure = false,
        )

        val config = draft.toRuntimeProfileConfig(
            allowRemoteSupabase = true,
            strictSupabaseConfig = true,
            userAgent = "onboarding-test-agent",
        )

        assertEquals(RuntimeMsakMode.STAGING, config.msakMode)
        assertEquals(RuntimeSupabaseMode.TESTING, config.supabaseMode)
        assertEquals("http://127.0.0.1:54321", config.localSupabaseUrl)
        assertEquals("local-key", config.localSupabaseApiKey)
        assertEquals("https://testing.example.com", config.testingSupabaseUrl)
        assertEquals("testing-key", config.testingSupabaseApiKey)
        assertEquals("https://live.example.com", config.liveSupabaseUrl)
        assertEquals("live-key", config.liveSupabaseApiKey)
        assertEquals("127.0.0.1:8080", config.localMsakHost)
        assertEquals(false, config.localMsakSecure)
        assertEquals(true, config.allowRemoteSupabase)
        assertEquals(true, config.strictSupabaseConfig)
        assertEquals("onboarding-test-agent", config.userAgent)
    }

    @Test
    fun fromConfig_roundTripsBackToDraft() {
        val config = RuntimeProfileConfig(
            msakMode = RuntimeMsakMode.PUBLIC,
            supabaseMode = RuntimeSupabaseMode.LIVE,
            localSupabaseUrl = "http://127.0.0.1:54321",
            localSupabaseApiKey = "local-key",
            testingSupabaseUrl = "https://testing.example.com",
            testingSupabaseApiKey = "testing-key",
            liveSupabaseUrl = "https://live.example.com",
            liveSupabaseApiKey = "live-key",
            localMsakHost = "127.0.0.1:8080",
            localMsakSecure = true,
        )

        val draft = RuntimeOnboardingContract.fromConfig(config)
        assertEquals(RuntimeMsakMode.PUBLIC, draft.msakMode)
        assertEquals(RuntimeSupabaseMode.LIVE, draft.supabaseMode)
        assertEquals("http://127.0.0.1:54321", draft.localSupabaseUrl)
        assertEquals("local-key", draft.localSupabaseApiKey)
        assertEquals("https://testing.example.com", draft.testingSupabaseUrl)
        assertEquals("testing-key", draft.testingSupabaseApiKey)
        assertEquals("https://live.example.com", draft.liveSupabaseUrl)
        assertEquals("live-key", draft.liveSupabaseApiKey)
        assertEquals("127.0.0.1:8080", draft.localMsakHost)
        assertEquals(true, draft.localMsakSecure)
    }
}
