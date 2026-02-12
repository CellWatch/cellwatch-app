package edu.gatech.cc.cellwatch.androidtestapp

import edu.gatech.cc.cellwatch.data.sync.SyncTransportTarget
import edu.gatech.cc.cellwatch.domain.fcc.MeasurementSequenceHarness
import edu.gatech.cc.cellwatch.domain.fcc.MeasurementSequenceHarnessResult
import edu.gatech.cc.cellwatch.domain.fcc.MsakLocateEnvironment
import edu.gatech.cc.cellwatch.domain.runtime.RuntimeSyncMsakProfiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.FileInputStream
import java.util.Properties
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class PublicMsakLocalSupabaseSmokeTest {

    @Test
    fun runPhase3Sequence_againstPublicMsak_withLocalSupabaseProfile_whenEnabled() {
        assumeTrue(
            "Set CELLWATCH_RUN_PUBLIC_MSAK_LOCAL_SUPABASE_SMOKE=1 to enable this Tier2 smoke test",
            System.getenv("CELLWATCH_RUN_PUBLIC_MSAK_LOCAL_SUPABASE_SMOKE") == "1",
        )

        val props = loadCellwatchProperties()
        val profile = RuntimeSyncMsakProfiles.publicMsakLocalSupabase(
            localSupabaseUrl = props.getProperty("SUPABASE_LOCAL_URL"),
            localSupabaseApiKey = props.getProperty("SUPABASE_LOCAL_API_KEY"),
            userAgent = "android-test-app-phase3-public-msak-local-supabase",
        )
        val resolvedSupabase = profile.resolveSyncSupabaseConfig()
        val syncRemoteProfile = profile.toSyncRemoteProfile()

        assertEquals(MsakLocateEnvironment.PROD, profile.msakConfig.environment)
        assertEquals(SyncTransportTarget.LOCAL, syncRemoteProfile.target)
        assertTrue(
            "Expected local-only Supabase URL, got ${resolvedSupabase.url}",
            resolvedSupabase.url.contains("127.0.0.1") || resolvedSupabase.url.contains("localhost"),
        )

        val latch = CountDownLatch(1)
        var result: MeasurementSequenceHarnessResult? = null
        var error: Throwable? = null

        val harness = MeasurementSequenceHarness(config = profile.msakConfig)
        harness.runDefaultScenario { value, throwable ->
            result = value
            error = throwable
            latch.countDown()
        }

        val completed = latch.await(90, TimeUnit.SECONDS)
        harness.close()
        assertTrue("Phase3 public MSAK smoke test timed out", completed)
        if (error != null) throw AssertionError("Phase3 public MSAK smoke failed", error)

        val nonNull = requireNotNull(result)
        assertNotNull(nonNull.groupId)
        assertTrue(nonNull.throughputMachine.isNotBlank())
        assertTrue(nonNull.latencyMachine.isNotBlank())
        assertEquals(3, nonNull.persistedMeasurements)
    }
}

private fun loadCellwatchProperties(): Properties {
    val propsFile = listOf(
        File("cellwatch.properties"),
        File("../cellwatch.properties"),
    ).firstOrNull { it.exists() }
        ?: throw AssertionError("Missing cellwatch.properties in test working directory")
    return Properties().apply {
        FileInputStream(propsFile).use(::load)
    }
}
