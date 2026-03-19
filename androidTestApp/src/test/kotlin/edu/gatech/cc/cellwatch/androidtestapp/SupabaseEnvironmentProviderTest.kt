package edu.gatech.cc.cellwatch.androidtestapp

import edu.gatech.cc.cellwatch.androidtestapp.sync.CellwatchPropertiesSupabaseEnvironmentProvider
import edu.gatech.cc.cellwatch.androidtestapp.sync.SupabaseTarget
import edu.gatech.cc.cellwatch.data.sync.SyncTransportTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class SupabaseEnvironmentProviderTest {

    @Test
    fun localTarget_usesDefaults_whenPropertiesMissing() {
        val tmp = Files.createTempDirectory("cw-supa-missing").toFile()
        val provider = CellwatchPropertiesSupabaseEnvironmentProvider(
            workingDir = tmp,
            allowRemote = false,
            env = emptyMap(),
        )

        val env = provider.resolve(SupabaseTarget.LOCAL)

        assertEquals(SupabaseTarget.LOCAL, env.target)
        assertEquals("http://10.0.2.2:54321", env.url)
        assertTrue(env.apiKey.isNotBlank())
    }

    @Test
    fun localTarget_normalizesAndroidLoopback() {
        val tmp = Files.createTempDirectory("cw-supa-local").toFile()
        tmp.resolve("cellwatch.properties").writeText(
            """
            SUPABASE_LOCAL_URL="http://10.0.2.2:54321"
            SUPABASE_LOCAL_API_KEY="local-key"
            """.trimIndent()
        )
        val provider = CellwatchPropertiesSupabaseEnvironmentProvider(
            workingDir = tmp,
            allowRemote = false,
            env = emptyMap(),
        )

        val env = provider.resolve(SupabaseTarget.LOCAL)

        assertEquals("http://10.0.2.2:54321", env.url)
        assertTrue(env.apiKey.isNotBlank())
    }

    @Test
    fun remoteTarget_isBlockedWithoutExplicitFlag() {
        val tmp = Files.createTempDirectory("cw-supa-remote-blocked").toFile()
        tmp.resolve("cellwatch.properties").writeText(
            """
            SUPABASE_URL="https://example.supabase.co"
            SUPABASE_API_KEY="remote-key"
            """.trimIndent()
        )
        val provider = CellwatchPropertiesSupabaseEnvironmentProvider(
            workingDir = tmp,
            allowRemote = false,
            env = emptyMap(),
        )

        try {
            provider.resolve(SupabaseTarget.REMOTE)
            throw AssertionError("expected resolve(REMOTE) to fail")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("blocked"))
        }
    }

    @Test
    fun remoteTarget_succeedsWhenFlagEnabled() {
        val tmp = Files.createTempDirectory("cw-supa-remote-allowed").toFile()
        tmp.resolve("cellwatch.properties").writeText(
            """
            SUPABASE_URL="https://example.supabase.co"
            SUPABASE_API_KEY="remote-key"
            """.trimIndent()
        )
        val provider = CellwatchPropertiesSupabaseEnvironmentProvider(
            workingDir = tmp,
            allowRemote = true,
            env = emptyMap(),
        )

        val env = provider.resolve(SupabaseTarget.REMOTE)

        assertEquals(SupabaseTarget.REMOTE, env.target)
        assertEquals("https://example.supabase.co", env.url)
        assertEquals("remote-key", env.apiKey)
    }

    @Test
    fun transportResolver_local_mapsToLocalEnvironment() {
        val tmp = Files.createTempDirectory("cw-supa-local-resolver").toFile()
        tmp.resolve("cellwatch.properties").writeText(
            """
            SUPABASE_LOCAL_URL="http://10.0.2.2:54321"
            SUPABASE_LOCAL_API_KEY="local-key"
            """.trimIndent()
        )
        val provider = CellwatchPropertiesSupabaseEnvironmentProvider(
            workingDir = tmp,
            allowRemote = false,
            env = emptyMap(),
        )

        val cfg = provider.resolve(SyncTransportTarget.LOCAL)

        assertEquals("http://10.0.2.2:54321", cfg.url)
        assertEquals("local-key", cfg.apiKey)
    }

    @Test
    fun transportResolver_remote_respectsRemoteBlock() {
        val tmp = Files.createTempDirectory("cw-supa-remote-resolver").toFile()
        tmp.resolve("cellwatch.properties").writeText(
            """
            SUPABASE_URL="https://example.supabase.co"
            SUPABASE_API_KEY="remote-key"
            """.trimIndent()
        )
        val provider = CellwatchPropertiesSupabaseEnvironmentProvider(
            workingDir = tmp,
            allowRemote = false,
            env = emptyMap(),
        )

        try {
            provider.resolve(SyncTransportTarget.REMOTE)
            throw AssertionError("expected resolver to block remote")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("blocked"))
        }
    }
}
