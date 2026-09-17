package edu.gatech.cc.cellwatch.domain.fcc

import edu.gatech.cc.cellwatch.domain.capability.PlatformCapabilityProvider
import edu.gatech.cc.cellwatch.domain.capability.PlatformCapabilitySnapshot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

/**
 * The executors enrich each measurement with a capability snapshot, and
 * deliberately fall back to the un-enriched measurement when that capture
 * fails - a missing snapshot should not sink a completed measurement.
 *
 * That fallback must not extend to coroutine cancellation. `captureSnapshot()`
 * is a suspend function, so a cancelled sequence unwinds through it; swallowing
 * that would let the orchestrator proceed to the next MSAK leg after the caller
 * asked it to stop.
 */
class MsakMeasurementExecutorPlatformCancellationTest {

    @Test
    fun runLatency_propagatesCancellation_insteadOfReturningUnenrichedMeasurement() = runBlocking {
        val captureStarted = CompletableDeferred<Unit>()
        val executor = MsakMeasurementExecutorPlatform.create(
            config = MsakMeasurementExecutorConfig(
                userAgent = "jvm-test",
                capabilityProvider = object : PlatformCapabilityProvider {
                    override suspend fun captureSnapshot(): PlatformCapabilitySnapshot {
                        captureStarted.complete(Unit)
                        awaitCancellation()
                    }
                },
            ),
        )

        var returnedNormally = false
        val job = launch(Dispatchers.Default) {
            executor.runLatency(
                server = testServer(),
                groupId = "group-1",
                measurementId = "latency-1",
            )
            returnedNormally = true
        }

        captureStarted.await()
        job.cancelAndJoin()

        assertFalse(
            returnedNormally,
            "runLatency swallowed cancellation and returned a measurement",
        )
    }

    @Test
    fun runThroughput_propagatesCancellation_insteadOfReturningUnenrichedMeasurement() = runBlocking {
        val captureStarted = CompletableDeferred<Unit>()
        val executor = MsakMeasurementExecutorPlatform.create(
            config = MsakMeasurementExecutorConfig(
                userAgent = "jvm-test",
                capabilityProvider = object : PlatformCapabilityProvider {
                    override suspend fun captureSnapshot(): PlatformCapabilitySnapshot {
                        captureStarted.complete(Unit)
                        awaitCancellation()
                    }
                },
            ),
        )

        var returnedNormally = false
        val job = launch(Dispatchers.Default) {
            executor.runThroughput(
                server = testServer(),
                direction = ThroughputDirection.DOWNLOAD,
                groupId = "group-1",
                measurementId = "download-1",
            )
            returnedNormally = true
        }

        captureStarted.await()
        job.cancelAndJoin()

        assertFalse(
            returnedNormally,
            "runThroughput swallowed cancellation and returned a measurement",
        )
    }

    @Test
    fun runLatency_stillFallsBackToUnenrichedMeasurement_whenCaptureFails() = runBlocking {
        val executor = MsakMeasurementExecutorPlatform.create(
            config = MsakMeasurementExecutorConfig(
                userAgent = "jvm-test",
                capabilityProvider = object : PlatformCapabilityProvider {
                    override suspend fun captureSnapshot(): PlatformCapabilitySnapshot {
                        throw IllegalStateException("capability capture unavailable")
                    }
                },
            ),
        )

        val measurement = executor.runLatency(
            server = testServer(),
            groupId = "group-1",
            measurementId = "latency-1",
        )

        assertEquals("latency-1", measurement.id)
        assertNotNull(measurement.latencyData)
        assertEquals(null, measurement.deviceManufacturer)
    }

    private fun testServer() = MsakServerEndpoint(
        machine = "msak-test.example",
        urls = emptyMap(),
        latencyUdpPort = null,
    )
}
