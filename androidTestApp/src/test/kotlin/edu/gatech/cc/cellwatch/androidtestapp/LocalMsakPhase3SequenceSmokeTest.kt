package edu.gatech.cc.cellwatch.androidtestapp

import edu.gatech.cc.cellwatch.domain.fcc.MeasurementSequenceHarness
import edu.gatech.cc.cellwatch.domain.fcc.MeasurementSequenceHarnessResult
import edu.gatech.cc.cellwatch.domain.fcc.MsakLocateConfig
import edu.gatech.cc.cellwatch.domain.fcc.MsakLocateEnvironment
import edu.gatech.cc.cellwatch.domain.capability.AndroidPlatformCapabilityProvider
import androidx.test.core.app.ApplicationProvider
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
class LocalMsakPhase3SequenceSmokeTest {

    @Test
    fun runPhase3Sequence_againstLocalMsak_whenEnabled() {
        assumeTrue(
            "Set CELLWATCH_RUN_LOCAL_MSAK_SMOKE=1 to enable local MSAK Tier2 smoke test",
            System.getenv("CELLWATCH_RUN_LOCAL_MSAK_SMOKE") == "1",
        )

        val propsFile = listOf(
            File("cellwatch.properties"),
            File("../cellwatch.properties"),
        ).firstOrNull { it.exists() }
            ?: throw AssertionError("Missing cellwatch.properties in test working directory")

        val props = Properties().apply {
            FileInputStream(propsFile).use { load(it) }
        }
        val localHost = props.getProperty("MSAK_LOCAL_SERVER_HOST")
            ?.trim()
            ?.trim('"')
            ?.let(::normalizeHostForRobolectric)
            .orEmpty()
        val localSecure = props.getProperty("MSAK_LOCAL_SERVER_SECURE")
            ?.trim()
            ?.trim('"')
            ?.toBooleanStrictOrNull()
            ?: false
        assertTrue("MSAK_LOCAL_SERVER_HOST must be set in cellwatch.properties", localHost.isNotEmpty())

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
            capabilityProvider = AndroidPlatformCapabilityProvider(
                ApplicationProvider.getApplicationContext(),
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
        if (error != null) {
            val capturedError = requireNotNull(error)
            val allowTransientSkip = System.getenv("CELLWATCH_ALLOW_LOCAL_MSAK_TRANSIENT_SKIP") == "1"
            if (allowTransientSkip && isTransientLocalMsakFailure(capturedError)) {
                assumeTrue("Skipping local MSAK transient failure due to CELLWATCH_ALLOW_LOCAL_MSAK_TRANSIENT_SKIP=1: ${capturedError.message}", false)
            }
            throw AssertionError("Phase3 local MSAK smoke failed", capturedError)
        }

        val nonNull = requireNotNull(result)
        assertNotNull(nonNull.groupId)
        assertTrue(nonNull.throughputMachine.isNotBlank())
        assertTrue(nonNull.latencyMachine.isNotBlank())
        assertEquals(3, nonNull.persistedMeasurements)
        assertEquals(nonNull.persistedMeasurements, nonNull.persistedMeasurementsWithCapabilitySupport)
        assertTrue(nonNull.persistedMeasurementsWithCapabilityNotes >= 0)
        assertTrue(nonNull.capabilityPersistenceSummary.startsWith("capabilityPersistence("))
        assertTrue(nonNull.capabilitySummary.startsWith("capabilities("))
    }
}

private fun isTransientLocalMsakFailure(error: Throwable?): Boolean {
    if (error == null) return false
    val joined = generateSequence(error) { it.cause }
        .joinToString(" | ") { "${it::class.simpleName}:${it.message.orEmpty()}" }
        .lowercase()
    return listOf(
        "authorizefailure",
        "no latency result",
        "timed out",
        "connection refused",
        "socket is not connected",
        "cannot connect",
    ).any { joined.contains(it) }
}

private fun normalizeHostForRobolectric(rawHost: String): String {
    return when {
        rawHost.startsWith("10.0.2.2:") -> "127.0.0.1:${rawHost.substringAfter(':')}"
        rawHost.startsWith("10.0.3.2:") -> "127.0.0.1:${rawHost.substringAfter(':')}"
        rawHost == "10.0.2.2" -> "127.0.0.1"
        rawHost == "10.0.3.2" -> "127.0.0.1"
        else -> rawHost
    }
}
