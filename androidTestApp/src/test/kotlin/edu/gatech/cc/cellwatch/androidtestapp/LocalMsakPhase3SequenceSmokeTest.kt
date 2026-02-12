package edu.gatech.cc.cellwatch.androidtestapp

import edu.gatech.cc.cellwatch.domain.fcc.MeasurementSequenceHarness
import edu.gatech.cc.cellwatch.domain.fcc.MeasurementSequenceHarnessResult
import edu.gatech.cc.cellwatch.domain.fcc.MsakLocateConfig
import edu.gatech.cc.cellwatch.domain.fcc.MsakLocateEnvironment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.FileInputStream
import java.util.Properties
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
class LocalMsakPhase3SequenceSmokeTest {

    @Test
    fun runPhase3Sequence_againstLocalMsak_whenEnabled() {
        assumeTrue(
            "Set CELLWATCH_RUN_LOCAL_MSAK_SMOKE=1 to enable local MSAK Tier2 smoke test",
            System.getenv("CELLWATCH_RUN_LOCAL_MSAK_SMOKE") == "1",
        )

        val props = Properties().apply {
            FileInputStream("cellwatch.properties").use { load(it) }
        }
        val localHost = props.getProperty("MSAK_LOCAL_SERVER_HOST")?.trim().orEmpty()
        val localSecure = props.getProperty("MSAK_LOCAL_SERVER_SECURE")?.trim()?.toBooleanStrictOrNull() ?: false
        assumeTrue(
            "MSAK_LOCAL_SERVER_HOST must be set in cellwatch.properties",
            localHost.isNotEmpty(),
        )

        val latch = CountDownLatch(1)
        var result: MeasurementSequenceHarnessResult? = null
        var error: Throwable? = null

        val harness = MeasurementSequenceHarness(
            config = MsakLocateConfig(
                environment = MsakLocateEnvironment.LOCAL,
                userAgent = "android-test-app-phase3-local-smoke",
                localServerHost = localHost,
                localServerSecure = localSecure,
            ),
        )

        harness.runDefaultScenario { value, throwable ->
            result = value
            error = throwable
            latch.countDown()
        }

        val completed = latch.await(90, TimeUnit.SECONDS)
        harness.close()
        assertTrue("Phase3 local MSAK smoke test timed out", completed)
        if (error != null) throw AssertionError("Phase3 local MSAK smoke failed", error)

        val nonNull = requireNotNull(result)
        assertNotNull(nonNull.groupId)
        assertTrue(nonNull.throughputMachine.isNotBlank())
        assertTrue(nonNull.latencyMachine.isNotBlank())
        assertEquals(3, nonNull.persistedMeasurements)
    }
}
