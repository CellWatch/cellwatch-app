package edu.gatech.cc.cellwatch.domain.msak.throughput

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThroughputStreamTest {
    lateinit var server: MockWebServer
    lateinit var listener: WebSocketTestListener

    @Before
    fun setup() {
        server = MockWebServer()
        listener = WebSocketTestListener()
        server.enqueue(MockResponse().withWebSocketUpgrade(listener))
    }

    private fun assertGreater(greater: Long, less: Long) {
        assertTrue("expected ${greater} > ${less}", greater > less)
    }

    @Test
    fun testDownloadBasicFlow() {
        val stream = ThroughputStream(
            1,
            server.url("/").toString(),
            ThroughputDirection.DOWNLOAD,
            avgMeasurementIntervalMillis = 200,
            maxMeasurementIntervalMillis = 200,
            minMeasurementIntervalMillis = 200,
        )

        runBlocking {
            stream.start()
            assertEquals(true, listener.waitOpened(100))
            var sent = 0L

            // when the server sends some bytes and a measurement, the stream should report an update
            sent += listener.sendBytes(50)
            sent += listener.sendMeasurement(50, 0, 10000)
            val update1 = withTimeoutOrNull(100) { stream.updatesChan.receive() }
            assertEquals(true, update1?.fromServer)
            assertEquals(50L, update1?.measurement?.Application?.BytesSent)
            assertEquals(0L, update1?.measurement?.Application?.BytesReceived)
            assertEquals(10000L, update1?.measurement?.ElapsedTime)

            // the stream should eventually report a measurement of its own
            sent += listener.sendBytes(25)
            val update2 = withTimeoutOrNull(200) { stream.updatesChan.receive() }
            assertEquals(false, update2?.fromServer)
            assertEquals(0L, update2?.measurement?.Application?.BytesSent)
            assertEquals(0L, update2?.measurement?.Network?.BytesSent)
            assertEquals(sent, update2?.measurement?.Application?.BytesReceived)
            assertGreater(update2?.measurement?.Network?.BytesReceived ?: 0, sent)
            assertGreater(update2?.measurement?.ElapsedTime ?: 0, 0)

            // the stream should also send its measurement to the server
            assertEquals(update2?.measurement, listener.takeMeasurement(100))

            // the websocket should close normally when the stream is stopped
            stream.stop()
            assertEquals(true, listener.waitClosing(100))
            assertEquals(1000, listener.closingCode)
            assertEquals(true, listener.waitClosed(1000))
            assertEquals(1000, listener.closeCode)
            assertEquals(false, listener.waitFailed(100))
            val result = withTimeoutOrNull(100) { stream.updatesChan.receiveCatching() }
            assertEquals(true, result?.isClosed)
            assertEquals(null, stream.error)

            // no byte message should have ever been sent by the stream
            assertEquals(null, listener.takeByteMessage(100))
        }
    }

    @Test
    fun testUploadBasicFlow() {
        val stream = ThroughputStream(
            1,
            server.url("/").toString(),
            ThroughputDirection.UPLOAD,
            // send less data and slow down so that measurements don't take forever to get to server
            minMessageSize = 1,
            maxMessageSize = 16,
            messageScalingFraction = 64,
            queueFullDelayMillis = 10,
            avgMeasurementIntervalMillis = 200,
            maxMeasurementIntervalMillis = 200,
            minMeasurementIntervalMillis = 200,
        )

        runBlocking {
            stream.start()
            assertEquals(true, listener.waitOpened(100))

            // the stream should send bytes to the server
            val bytes1 = listener.takeByteMessage(100)
            assertNotNull(bytes1)
            assertEquals(1, bytes1?.size)

            // the stream should periodically double the bytes sent
            // start with 1
            // after sending 65 times, double (1 < 1*65/64)
            // after sending 32 more times, double (2 < (1*65 + 2*32)/64)
            var lastBytes = bytes1!!
            for (i in 1..100) {
                var bytes = listener.takeByteMessage(100)
                if (i == 65 || i == 97) {
                    assertEquals(lastBytes.size * 2, bytes?.size)
                } else {
                    assertEquals(lastBytes.size, bytes?.size)
                }
                lastBytes = bytes!!
            }

            // the stream should send measurements as updates and to the server
            val update1 = withTimeoutOrNull(250) { stream.updatesChan.receive() }
            assertEquals(false, update1?.fromServer)
            assertGreater(update1?.measurement?.Application?.BytesSent ?: 0, 0)
            assertEquals(0L, update1?.measurement?.Application?.BytesReceived)
            assertGreater(update1?.measurement?.Network?.BytesSent ?: 0, 0)
            assertEquals(0L, update1?.measurement?.Network?.BytesReceived)
            assertGreater(update1?.measurement?.ElapsedTime ?: 0, 0)
            assertEquals(update1?.measurement, listener.takeMeasurement(100))

            val update2 = withTimeoutOrNull(250) { stream.updatesChan.receive() }
            assertEquals(false, update2?.fromServer)
            assertGreater(update2?.measurement?.Application?.BytesSent ?: 0, update1?.measurement?.Application?.BytesSent ?: 0)
            assertEquals(0L, update2?.measurement?.Application?.BytesReceived)
            assertGreater(update2?.measurement?.Network?.BytesSent ?: 0, update1?.measurement?.Network?.BytesSent ?: 0)
            assertEquals(0L, update2?.measurement?.Network?.BytesReceived)
            assertGreater(update2?.measurement?.ElapsedTime ?: 0, update1?.measurement?.ElapsedTime ?: 0)
            assertEquals(update2?.measurement, listener.takeMeasurement(100))

            // the websocket should close normally when the stream is stopped
            stream.stop()
            assertEquals(true, listener.waitClosing(100))
            assertEquals(1000, listener.closingCode)
            assertEquals(true, listener.waitClosed(1000))
            assertEquals(1000, listener.closeCode)
            assertEquals(false, listener.waitFailed(100))
            assertEquals(null, stream.error)
            assertEquals(null, stream.error)
        }
    }

    @Test
    fun testMalformedMeasurement() {
        val stream = ThroughputStream(
            1,
            server.url("/").toString(),
            ThroughputDirection.DOWNLOAD,
            avgMeasurementIntervalMillis = 200,
            maxMeasurementIntervalMillis = 200,
            minMeasurementIntervalMillis = 200,
        )

        runBlocking {
            stream.start()
            assertEquals(true, listener.waitOpened(100))
            listener.send("This is not a measurement!")
            listener.sendMeasurement(50, 0, 10000)

            // the valid measurement should be received and sent as an update
            val update1 = withTimeoutOrNull(100) { stream.updatesChan.receive() }
            assertEquals(true, update1?.fromServer)
            assertEquals(50L, update1?.measurement?.Application?.BytesSent)
            assertEquals(0L, update1?.measurement?.Application?.BytesReceived)
            assertEquals(10000L, update1?.measurement?.ElapsedTime)

            // the websocket should close normally when the stream is stopped
            stream.stop()
            assertEquals(true, listener.waitClosed(1000))
            assertEquals(1000, listener.closeCode)
            assertEquals(false, listener.waitFailed(100))
            assertEquals(null, stream.error)
        }
    }

    @Test
    fun testServerCloseNormal() {
        val stream = ThroughputStream(
            1,
            server.url("/").toString(),
            ThroughputDirection.DOWNLOAD,
            avgMeasurementIntervalMillis = 200,
            maxMeasurementIntervalMillis = 200,
            minMeasurementIntervalMillis = 200,
        )

        runBlocking {
            stream.start()
            assertEquals(true, listener.waitOpened(100))
            listener.close(1000)
            assertEquals(true, listener.waitClosed(100))
            assertEquals(false, listener.waitFailed(100))
            val result = withTimeoutOrNull(100) { stream.updatesChan.receiveCatching() }
            assertEquals(true, result?.isClosed)
            assertEquals(null, stream.error)
        }
    }

    @Test
    fun testServerCloseError() {
        val stream = ThroughputStream(
            1,
            server.url("/").toString(),
            ThroughputDirection.DOWNLOAD,
            avgMeasurementIntervalMillis = 200,
            maxMeasurementIntervalMillis = 200,
            minMeasurementIntervalMillis = 200,
        )

        runBlocking {
            stream.start()
            assertEquals(true, listener.waitOpened(100))
            listener.close(1003)
            assertEquals(true, listener.waitClosed(100))
            assertEquals(false, listener.waitFailed(100))
            val result = withTimeoutOrNull(100) { stream.updatesChan.receiveCatching() }
            assertEquals(true, result?.isClosed)
            assertNotNull(stream.error)
        }
    }

    @Test
    fun testServerDie() {
        val stream = ThroughputStream(
            1,
            server.url("/").toString(),
            ThroughputDirection.DOWNLOAD,
            avgMeasurementIntervalMillis = 200,
            maxMeasurementIntervalMillis = 200,
            minMeasurementIntervalMillis = 200,
        )


        runBlocking {
            stream.start()
            assertEquals(true, listener.waitOpened(100))
            server.shutdown()
            val result = withTimeoutOrNull(100) { stream.updatesChan.receiveCatching() }
            assertEquals(true, result?.isClosed)
            assertNotNull(stream.error)
        }
    }
}
