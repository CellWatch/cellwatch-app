package edu.gatech.cc.cellwatch.androidtestapp.sync

import edu.gatech.cc.cellwatch.domain.runtime.RuntimeMsakMode
import edu.gatech.cc.cellwatch.domain.runtime.RuntimeSupabaseMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Properties

class RuntimeProfileResolverTest {

    @Test
    fun resolveRuntimeProfileFromProperties_localMsakDefaultsToAndroidHostWhenUnset() {
        val props = Properties().apply {
            setProperty("SUPABASE_LOCAL_URL", "http://10.0.2.2:54321")
            setProperty("SUPABASE_LOCAL_API_KEY", "local-key")
        }

        val profile = resolveRuntimeProfileFromProperties(
            preloadedProperties = props,
            msakMode = RuntimeMsakMode.LOCAL,
            supabaseMode = RuntimeSupabaseMode.LOCAL,
            allowRemoteSupabase = false,
        )

        assertEquals("10.0.2.2:8080", profile.msakConfig.localServerHost)
    }

    @Test
    fun resolveRuntimeProfileFromProperties_nonLocalMsakDoesNotForceLocalHost() {
        val props = Properties().apply {
            setProperty("SUPABASE_LOCAL_URL", "http://10.0.2.2:54321")
            setProperty("SUPABASE_LOCAL_API_KEY", "local-key")
        }

        val profile = resolveRuntimeProfileFromProperties(
            preloadedProperties = props,
            msakMode = RuntimeMsakMode.PUBLIC,
            supabaseMode = RuntimeSupabaseMode.LOCAL,
            allowRemoteSupabase = false,
        )

        assertNull(profile.msakConfig.localServerHost)
    }

    @Test
    fun resolveRuntimeProfileFromProperties_prefersEnvironmentOverProperties() {
        val props = Properties().apply {
            setProperty("SUPABASE_LOCAL_URL", "http://properties.example:54321")
            setProperty("SUPABASE_LOCAL_API_KEY", "properties-key")
        }
        val env = mapOf(
            "SUPABASE_LOCAL_URL" to "http://10.0.2.2:54321",
            "SUPABASE_LOCAL_API_KEY" to "env-key",
        )

        val profile = resolveRuntimeProfileFromProperties(
            preloadedProperties = props,
            msakMode = RuntimeMsakMode.PUBLIC,
            supabaseMode = RuntimeSupabaseMode.LOCAL,
            allowRemoteSupabase = false,
            env = env,
        )
        val resolved = profile.resolveSyncSupabaseConfig()

        assertEquals("http://10.0.2.2:54321", resolved.url)
        assertEquals("env-key", resolved.apiKey)
    }
}
