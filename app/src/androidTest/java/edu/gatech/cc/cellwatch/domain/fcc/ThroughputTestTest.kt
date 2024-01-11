package edu.gatech.cc.cellwatch.domain.fcc

import androidx.test.ext.junit.runners.AndroidJUnit4
import edu.gatech.cc.cellwatch.domain.msak.Server
import edu.gatech.cc.cellwatch.domain.msak.THROUGHPUT_DOWNLOAD_PATH
import edu.gatech.cc.cellwatch.domain.msak.THROUGHPUT_UPLOAD_PATH
import edu.gatech.cc.cellwatch.domain.msak.throughput.ByteCounters
import edu.gatech.cc.cellwatch.domain.msak.throughput.ThroughputDirection
import edu.gatech.cc.cellwatch.domain.msak.throughput.ThroughputMeasurement
import edu.gatech.cc.cellwatch.domain.msak.throughput.ThroughputUpdate
import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.datetime.Clock
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith


@RunWith(AndroidJUnit4::class)
class ThroughputTestTest {
    lateinit var test: ThroughputTest

    fun setup(maxWarmupTime: Long = 100, maxActiveTime: Long = 100, direction: ThroughputDirection = ThroughputDirection.DOWNLOAD) {
        val server = Server("test", null, mapOf(
            "ws:///$THROUGHPUT_DOWNLOAD_PATH" to "ws://0.0.0.0/$THROUGHPUT_DOWNLOAD_PATH",
            "ws:///$THROUGHPUT_UPLOAD_PATH" to "ws://0.0.0.0/$THROUGHPUT_UPLOAD_PATH",
        ))

        test = ThroughputTest(server, 3, direction, maxWarmupTime = maxWarmupTime, maxActiveTime = maxActiveTime)
        mockkObject(test.msakTest)
        test.msakTest.streams.forEach {
            mockkObject(it)
            every { it.start() } just runs
        }
    }

    suspend fun sendUpdate(stream: Int, measurement: ThroughputMeasurement, fromServer: Boolean = false, waitForReceive: Boolean = false) {
        val update = ThroughputUpdate(fromServer, stream, Clock.System.now(), measurement)
        (test.msakTest.streams[stream].updates as ArrayList).add(update)
        (test.msakTest.updatesChan as Channel).send(update)
        if (waitForReceive) test.metricsChan.receive()
    }

    fun assertBetween(min: Long, max: Long, actual: Long) {
        assertEquals("expected number between $min and $max, got $actual", true, actual in min..max)
    }

    fun assertLessThan(max: Long, actual: Long) {
        assertEquals("expected $actual to be < $max", true, actual < max)
    }

    @Test(timeout=3000)
    fun testBasicFlow() {
        setup(200, 300)

        runBlocking {
            val preStart = Clock.System.now()
            val defResult = async { test.run() }
            withTimeout(200) { while (!test.msakTest.started) delay(10) }
            verify { test.msakTest.start() }
            val postStart = Clock.System.now()

            sendUpdate(0, ThroughputMeasurement(
                ByteCounters(0, 20),
                ByteCounters(0, 10),
                10,
            ))
            sendUpdate(1, ThroughputMeasurement(
                ByteCounters(0, 21),
                ByteCounters(0, 11),
                11,
            ))
            sendUpdate(2, ThroughputMeasurement(
                ByteCounters(0, 22),
                ByteCounters(0, 12),
                12,
            ))

            delay(200 - (Clock.System.now() - postStart).inWholeMilliseconds) // wait for warmup to end

            sendUpdate(0, ThroughputMeasurement(
                ByteCounters(0, 120),
                ByteCounters(0, 110),
                210,
            ))
            sendUpdate(1, ThroughputMeasurement(
                ByteCounters(0, 121),
                ByteCounters(0, 111),
                211,
            ))
            sendUpdate(2, ThroughputMeasurement(
                ByteCounters(0, 122),
                ByteCounters(0, 112),
                212,
            ))

            val result = withTimeout(500) { defResult.await() }
            assertEquals("0.0.0.0", result.targetHost)
            assertEquals(true, result.success)
            assertEquals(true, result.start > preStart && result.start < postStart)
            assertEquals(63L, result.warmupMetrics?.bytes)
            assertBetween(175000, 225000, result.warmupMetrics?.usecs ?: 0)
            assertEquals(300L, result.activeMetrics?.bytes)
            assertBetween(275000, 325000, result.activeMetrics?.usecs ?: 0)
        }
    }

    @Test(timeout=3000)
    fun testUpdateForwarding() {
        setup()

        runBlocking {
            launch { test.run() }
            withTimeout(200) { while (!test.msakTest.started) delay(10) }

            sendUpdate(0, ThroughputMeasurement(
                ByteCounters(0, 20),
                ByteCounters(0, 10),
                10,
            ))

            val metrics1 = test.metricsChan.receive()
            assertEquals(20, metrics1.bytes)
            assertEquals(true, metrics1.usecs > 0)

            sendUpdate(1, ThroughputMeasurement(
                ByteCounters(0, 21),
                ByteCounters(0, 11),
                11,
            ))

            val metrics2 = test.metricsChan.receive()
            assertEquals(41, metrics2.bytes)
            assertEquals(true, metrics2.usecs > metrics1.usecs)

            sendUpdate(2, ThroughputMeasurement(
                ByteCounters(0, 22),
                ByteCounters(0, 12),
                12,
            ))

            val metrics3 = test.metricsChan.receive()
            assertEquals(63, metrics3.bytes)
            assertEquals(true, metrics3.usecs > metrics2.usecs)
        }
    }

    @Test(timeout=3000)
    fun testWarmupToActive() {
        setup(1000)

        runBlocking {
            val defResult = async { test.run() }
            withTimeout(200) { while (!test.msakTest.started) delay(10) }

            // first round of updates, should be in warmup
            sendUpdate(0, ThroughputMeasurement(
                ByteCounters(0, 20),
                ByteCounters(0, 10),
                10,
            ), waitForReceive = true)
            sendUpdate(1, ThroughputMeasurement(
                ByteCounters(0, 21),
                ByteCounters(0, 11),
                11,
            ), waitForReceive = true)
            sendUpdate(2, ThroughputMeasurement(
                ByteCounters(0, 22),
                ByteCounters(0, 12),
                12,
            ), waitForReceive = true)

            // second round of updates (increasing), should be in warmup
            sendUpdate(0, ThroughputMeasurement(
                ByteCounters(0, 120),
                ByteCounters(0, 110),
                20,
            ), waitForReceive = true)
            sendUpdate(1, ThroughputMeasurement(
                ByteCounters(0, 121),
                ByteCounters(0, 111),
                21,
            ), waitForReceive = true)
            sendUpdate(2, ThroughputMeasurement(
                ByteCounters(0, 122),
                ByteCounters(0, 112),
                22,
            ), waitForReceive = true)

            // third update (decreasing rate) from first two streams, should be in warmup
            sendUpdate(0, ThroughputMeasurement(
                ByteCounters(0, 160),
                ByteCounters(0, 150),
                30,
            ), waitForReceive = true)
            sendUpdate(1, ThroughputMeasurement(
                ByteCounters(0, 161),
                ByteCounters(0, 151),
                31,
            ), waitForReceive = true)

            // fourth update (increasing rate, from first two streams, should be in warmup
            sendUpdate(0, ThroughputMeasurement(
                ByteCounters(0, 360),
                ByteCounters(0, 350),
                40,
            ), waitForReceive = true)
            sendUpdate(1, ThroughputMeasurement(
                ByteCounters(0, 361),
                ByteCounters(0, 351),
                41,
            ), waitForReceive = true)

            // third update (decreasing rate) from last stream, ends warmup
            sendUpdate(2, ThroughputMeasurement(
                ByteCounters(0, 162),
                ByteCounters(0, 152),
                42,
            ), waitForReceive = true)

            // one more update from each stream, should be in active
            sendUpdate(0, ThroughputMeasurement(
                ByteCounters(0, 460),
                ByteCounters(0, 450),
                20,
            ), waitForReceive = true)
            sendUpdate(1, ThroughputMeasurement(
                ByteCounters(0, 461),
                ByteCounters(0, 451),
                21,
            ), waitForReceive = true)
            sendUpdate(2, ThroughputMeasurement(
                ByteCounters(0, 462),
                ByteCounters(0, 452),
                22,
            ), waitForReceive = true)

            val result = defResult.await()
            assertEquals(883L, result.warmupMetrics?.bytes)
            assertEquals(500L, result.activeMetrics?.bytes)

            // make sure warmup wasn't ended by its max duration
            assertLessThan(1000000, result.warmupMetrics?.usecs ?: 1000000)
        }
    }

    @Test(timeout=3000)
    fun testNoUpdatesInWarmup() {
        setup()

        runBlocking {
            val defResult = async { test.run() }
            withTimeout(200) { while (!test.msakTest.started) delay(10) }

            sendUpdate(0, ThroughputMeasurement(
                ByteCounters(0, 20),
                ByteCounters(0, 10),
                10,
            ))
            sendUpdate(1, ThroughputMeasurement(
                ByteCounters(0, 21),
                ByteCounters(0, 11),
                11,
            ))

            val result = defResult.await()
            assertEquals(false, result.success)
        }
    }

    @Test(timeout=3000)
    fun testEndInWarmup() {
        setup(1000)

        runBlocking {
            val defResult = async { test.run() }
            withTimeout(200) { while (!test.msakTest.started) delay(10) }

            sendUpdate(0, ThroughputMeasurement(
                ByteCounters(0, 20),
                ByteCounters(0, 10),
                10,
            ))
            sendUpdate(1, ThroughputMeasurement(
                ByteCounters(0, 21),
                ByteCounters(0, 11),
                11,
            ))
            sendUpdate(2, ThroughputMeasurement(
                ByteCounters(0, 22),
                ByteCounters(0, 12),
                12,
            ))
            (test.msakTest.updatesChan as Channel).close()

            val result = defResult.await()
            assertEquals(false, result.success)
        }
    }

    @Test(timeout=3000)
    fun testNoActiveMeasurements() {
        setup(300, 300)

        runBlocking {
            val defResult = async { test.run() }
            withTimeout(200) { while (!test.msakTest.started) delay(10) }

            sendUpdate(0, ThroughputMeasurement(
                ByteCounters(0, 20),
                ByteCounters(0, 10),
                10,
            ))
            sendUpdate(1, ThroughputMeasurement(
                ByteCounters(0, 21),
                ByteCounters(0, 11),
                11,
            ))
            sendUpdate(2, ThroughputMeasurement(
                ByteCounters(0, 22),
                ByteCounters(0, 12),
                12,
            ))

            delay(300) // wait for warmup to end

            sendUpdate(1, ThroughputMeasurement(
                ByteCounters(0, 41),
                ByteCounters(0, 31),
                21,
            ))
            sendUpdate(2, ThroughputMeasurement(
                ByteCounters(0, 42),
                ByteCounters(0, 32),
                22,
            ))
            (test.msakTest.updatesChan as Channel).close()

            val result = defResult.await()
            assertEquals(false, result.success)
        }
    }
    @Test(timeout=3000)
    fun testUpdateFilteringDownload() {
        setup(1000)

        runBlocking {
            val defResult = async { test.run() }
            withTimeout(200) { while (!test.msakTest.started) delay(10) }

            // updates from client, should all be forwarded
            sendUpdate(0, ThroughputMeasurement(
                ByteCounters(0, 200),
                ByteCounters(0, 100),
                10,
            ), waitForReceive = true)
            sendUpdate(1, ThroughputMeasurement(
                ByteCounters(0, 201),
                ByteCounters(0, 101),
                11,
            ), waitForReceive = true)
            sendUpdate(2, ThroughputMeasurement(
                ByteCounters(0, 202),
                ByteCounters(0, 102),
                12,
            ), waitForReceive = true)

            // updates from server, should not be forwarded and should not end warmup
            sendUpdate(0, ThroughputMeasurement(
                ByteCounters(0, 250),
                ByteCounters(0, 150),
                20,
            ), fromServer = true)
            sendUpdate(1, ThroughputMeasurement(
                ByteCounters(0, 251),
                ByteCounters(0, 151),
                21,
            ), fromServer = true)
            sendUpdate(2, ThroughputMeasurement(
                ByteCounters(0, 252),
                ByteCounters(0, 152),
                22,
            ), fromServer = true)

            delay(50) // give some time to be sure updates weren't forwarded
            assertEquals(true, test.metricsChan.isEmpty)

            // updates from client, should all be forwarded and should end warmup
            sendUpdate(0, ThroughputMeasurement(
                ByteCounters(0, 220),
                ByteCounters(0, 120),
                30,
            ), waitForReceive = true)
            sendUpdate(1, ThroughputMeasurement(
                ByteCounters(0, 221),
                ByteCounters(0, 121),
                31,
            ), waitForReceive = true)
            sendUpdate(2, ThroughputMeasurement(
                ByteCounters(0, 222),
                ByteCounters(0, 122),
                32,
            ), waitForReceive = true)

            // updates from server, should not be forwarded and should not be used for active metrics
            sendUpdate(0, ThroughputMeasurement(
                ByteCounters(0, 480),
                ByteCounters(0, 380),
                40,
            ), fromServer = true)
            sendUpdate(1, ThroughputMeasurement(
                ByteCounters(0, 481),
                ByteCounters(0, 381),
                41,
            ), fromServer = true)
            sendUpdate(2, ThroughputMeasurement(
                ByteCounters(0, 482),
                ByteCounters(0, 382),
                42,
            ), fromServer = true)

            delay(50) // give some time to be sure updates weren't forwarded
            assertEquals(true, test.metricsChan.isEmpty)

            // updates from client, should all be forwarded and should be used for active metrics
            sendUpdate(0, ThroughputMeasurement(
                ByteCounters(0, 420),
                ByteCounters(0, 320),
                50,
            ), waitForReceive = true)
            sendUpdate(1, ThroughputMeasurement(
                ByteCounters(0, 421),
                ByteCounters(0, 321),
                51,
            ), waitForReceive = true)
            sendUpdate(2, ThroughputMeasurement(
                ByteCounters(0, 422),
                ByteCounters(0, 322),
                52,
            ), waitForReceive = true)

            val result = defResult.await()
            assertEquals(663L, result.warmupMetrics?.bytes)
            assertLessThan(1000000, result.warmupMetrics?.usecs ?: 1000000)
            assertEquals(600L, result.activeMetrics?.bytes)
        }
    }

    @Test(timeout=3000)
    fun testUpdateFilteringUpload() {
        setup(1000, direction = ThroughputDirection.UPLOAD)

        runBlocking {
            val defResult = async { test.run() }
            withTimeout(200) { while (!test.msakTest.started) delay(10) }

            // updates from server, should all be forwarded
            sendUpdate(0, ThroughputMeasurement(
                ByteCounters(0, 200),
                ByteCounters(0, 100),
                10,
            ), fromServer = true, waitForReceive = true)
            sendUpdate(1, ThroughputMeasurement(
                ByteCounters(0, 201),
                ByteCounters(0, 101),
                11,
            ), fromServer = true, waitForReceive = true)
            sendUpdate(2, ThroughputMeasurement(
                ByteCounters(0, 202),
                ByteCounters(0, 102),
                12,
            ), fromServer = true, waitForReceive = true)

            // updates from client, should not be forwarded and should not end warmup
            sendUpdate(0, ThroughputMeasurement(
                ByteCounters(0, 250),
                ByteCounters(0, 150),
                20,
            ))
            sendUpdate(1, ThroughputMeasurement(
                ByteCounters(0, 251),
                ByteCounters(0, 151),
                21,
            ))
            sendUpdate(2, ThroughputMeasurement(
                ByteCounters(0, 252),
                ByteCounters(0, 152),
                22,
            ))

            delay(50) // give some time to be sure updates weren't forwarded
            assertEquals(true, test.metricsChan.isEmpty)

            // updates from server, should all be forwarded and should end warmup
            sendUpdate(0, ThroughputMeasurement(
                ByteCounters(0, 220),
                ByteCounters(0, 120),
                30,
            ), fromServer = true, waitForReceive = true)
            sendUpdate(1, ThroughputMeasurement(
                ByteCounters(0, 221),
                ByteCounters(0, 121),
                31,
            ), fromServer = true, waitForReceive = true)
            sendUpdate(2, ThroughputMeasurement(
                ByteCounters(0, 222),
                ByteCounters(0, 122),
                32,
            ), fromServer = true, waitForReceive = true)

            // updates from client, should not be forwarded and should not be used for active metrics
            sendUpdate(0, ThroughputMeasurement(
                ByteCounters(0, 480),
                ByteCounters(0, 380),
                40,
            ))
            sendUpdate(1, ThroughputMeasurement(
                ByteCounters(0, 481),
                ByteCounters(0, 381),
                41,
            ))
            sendUpdate(2, ThroughputMeasurement(
                ByteCounters(0, 482),
                ByteCounters(0, 382),
                42,
            ))

            delay(50) // give some time to be sure updates weren't forwarded
            assertEquals(true, test.metricsChan.isEmpty)

            // updates from server, should all be forwarded and should be used for active metrics
            sendUpdate(0, ThroughputMeasurement(
                ByteCounters(0, 420),
                ByteCounters(0, 320),
                50,
            ), fromServer = true, waitForReceive = true)
            sendUpdate(1, ThroughputMeasurement(
                ByteCounters(0, 421),
                ByteCounters(0, 321),
                51,
            ), fromServer = true, waitForReceive = true)
            sendUpdate(2, ThroughputMeasurement(
                ByteCounters(0, 422),
                ByteCounters(0, 322),
                52,
            ), fromServer = true, waitForReceive = true)

            val result = defResult.await()
            assertEquals(663L, result.warmupMetrics?.bytes)
            assertLessThan(1000000, result.warmupMetrics?.usecs ?: 1000000)
            assertEquals(600L, result.activeMetrics?.bytes)
        }
    }
}