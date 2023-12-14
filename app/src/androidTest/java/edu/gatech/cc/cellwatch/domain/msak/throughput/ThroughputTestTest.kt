package edu.gatech.cc.cellwatch.domain.msak.throughput

import androidx.test.ext.junit.runners.AndroidJUnit4
import edu.gatech.cc.cellwatch.domain.msak.Server
import edu.gatech.cc.cellwatch.domain.msak.THROUGHPUT_DOWNLOAD_PATH
import edu.gatech.cc.cellwatch.domain.msak.THROUGHPUT_UPLOAD_PATH
import com.google.gson.Gson
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.WebSocket
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThroughputTestTest {
    lateinit var test: ThroughputTest

    fun setup(duration: Long = 1000, delay: Long = 0, serverEndTimeGraceMillis: Long = 5000) {
        test = ThroughputTest(
            Server("localhost", null, mapOf(
                "ws:///$THROUGHPUT_DOWNLOAD_PATH" to "ws://localhost/download",
                "ws:///$THROUGHPUT_UPLOAD_PATH" to "ws://localhost/upload",
            )),
            ThroughputDirection.DOWNLOAD,
            3,
            duration = duration,
            delay = delay,
            serverEndTimeGraceMillis = serverEndTimeGraceMillis,
        )

        test.streams.forEach {
            mockkObject(it)
            every { it.start() } just runs
        }
    }

    @After
    fun cleanup() {
        test.stop()
    }

    @Test
    fun testStartStop() {
        setup()

        test.start()
        assertEquals(true, test.started)
        assertNotNull(test.startTime)

        runBlocking { delay(10) }
        test.streams.forEach { verify { it.start() } }

        test.stop()
        test.streams.forEach { verify { it.stop() }}
        assertEquals(true, test.ended)
        assertNotNull(test.endTime)
    }

    @Test
    fun testDelay() {
        setup(delay = 100)
        test.start()

        runBlocking { delay(10) }
        verify(exactly = 1) { test.streams[0].start() }
        verify(exactly = 0) { test.streams[1].start() }
        verify(exactly = 0) { test.streams[2].start() }

        runBlocking { delay(100) }
        verify(exactly = 1) { test.streams[0].start() }
        verify(exactly = 1) { test.streams[1].start() }
        verify(exactly = 0) { test.streams[2].start() }

        runBlocking { delay(100) }
        verify(exactly = 1) { test.streams[0].start() }
        verify(exactly = 1) { test.streams[1].start() }
        verify(exactly = 1) { test.streams[2].start() }
    }

    @Test
    fun testUpdates() {
        setup()
        test.start()

        test.streams.forEachIndexed { index, stream ->
            // sort of gross, but I can't figure out how else to trigger an update being sent
            val ws = mockk<WebSocket>()
            val measurement = ThroughputMeasurement(null, ByteCounters(5, 0), 10)
            stream.onMessage(ws, Gson().toJson(measurement))

            val update = runBlocking { withTimeoutOrNull(10) { test.updatesChan.receive() } }
            assertEquals(index, update?.stream)
            assertEquals(measurement, update?.measurement)
        }
    }

    @Test
    fun testDuration() {
        setup(duration = 100, serverEndTimeGraceMillis = 0)
        test.start()

        runBlocking { delay(10) }
        test.streams.forEach { verify { it.start() }}
        test.streams.forEach { verify(exactly = 0) { it.stop() }}

        runBlocking { delay(100) }
        test.streams.forEach { verify { it.stop() }}
        assertEquals(true, test.ended)
        assertNotNull(test.endTime)

        assertEquals(true, (test.endTime!! - test.startTime!!).inWholeMilliseconds >= 100)
    }
}
