package edu.gatech.cc.cellwatch.domain.msak.latency

import androidx.test.ext.junit.runners.AndroidJUnit4
import edu.gatech.cc.cellwatch.domain.msak.LATENCY_AUTHORIZE_PATH
import edu.gatech.cc.cellwatch.domain.msak.LATENCY_CHARSET
import edu.gatech.cc.cellwatch.domain.msak.LATENCY_RESULT_PATH
import edu.gatech.cc.cellwatch.domain.msak.Server
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import java.net.BindException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class LatencyTestTest {
    private lateinit var socket: DatagramSocket
    private lateinit var server: MockWebServer
    private lateinit var msakServer: Server
    private lateinit var testAddr: InetAddress
    private var testPort = 0

    companion object {
        var serverPort = 1234
    }

    private fun setup(
        authResponse: MockResponse = MockResponse().setBody("{\"Type\": \"c2s\", \"ID\": \"fakeid\", \"Seq\": 0}"),
        resultResponse: MockResponse = MockResponse().setBody("""{
            "ID": "fakeid",
            "StartTime": "2023-11-30T14:48:20.123Z",
            "RoundTrips": [
                {"RTT": 10, "Lost": false},
                {"RTT": 20, "Lost": false},
                {"Lost": true}
            ],
            "PacketsSent": 3,
            "PacketsReceived": 2
        }""".trimIndent()),
    ) {
        for (i in 1..3) {
            try {
                socket = DatagramSocket(serverPort, InetAddress.getLocalHost())
                break
            } catch (e: BindException) {
                serverPort++
            }
        }
        socket.soTimeout = 100

        server = MockWebServer()
        server.enqueue(authResponse)
        server.enqueue(resultResponse)
        server.start()

        msakServer = Server(server.hostName, null, mapOf(
            "http:///$LATENCY_AUTHORIZE_PATH" to server.url("/$LATENCY_AUTHORIZE_PATH").toString(),
            "http:///$LATENCY_RESULT_PATH" to server.url("/$LATENCY_RESULT_PATH").toString(),
        ))
    }

    private fun expectPacket(payload: LatencyMessage) {
        val buf = ByteArray(1024)
        val pkt = DatagramPacket(buf, buf.size)
        socket.receive(pkt)
        testAddr = pkt.address
        testPort = pkt.port
        assertEquals(payload, Gson().fromJson(buf.sliceArray(IntRange(0, pkt.length - 1)).toString(LATENCY_CHARSET), LatencyMessage::class.java))
    }

    private fun sendPacket(payload: LatencyMessage) {
        val buf = Gson().toJson(payload).toByteArray(LATENCY_CHARSET)
        val pkt = DatagramPacket(buf, buf.size)
        pkt.address = testAddr
        pkt.port = testPort
        socket.send(pkt)
    }

    @Test
    fun testBasicFlow() {
        setup()
        val test = LatencyTest(msakServer, latencyPort = serverPort, duration = 50)
        test.start()

        val authReq = server.takeRequest(100, TimeUnit.MILLISECONDS)
        assertEquals("/$LATENCY_AUTHORIZE_PATH", authReq?.path)

        expectPacket(LatencyMessage("c2s", "fakeid", 0, null))

        sendPacket(LatencyMessage("s2c", "fakeid", 1, null))
        expectPacket(LatencyMessage("s2c", "fakeid", 1, null))

        sendPacket(LatencyMessage("s2c", "fakeid", 2, 10))
        expectPacket(LatencyMessage("s2c", "fakeid", 2, 10))

        sendPacket(LatencyMessage("s2c", "fakeid", 3, 20))
        expectPacket(LatencyMessage("s2c", "fakeid", 3, 20))

        runBlocking { while (!test.updatesChan.receiveCatching().isClosed); }

        val resultReq = server.takeRequest(100, TimeUnit.MILLISECONDS)
        assertEquals("/$LATENCY_RESULT_PATH", resultReq?.path)

        assertEquals("fakeid", test.result?.ID)
        assertEquals("2023-11-30T14:48:20.123Z", test.result?.StartTime)
        assertEquals(3, test.result?.PacketsSent)
        assertEquals(2, test.result?.PacketsReceived)
        assertArrayEquals(arrayOf(
            LatencyRoundTrip(10, false),
            LatencyRoundTrip(20, false),
            LatencyRoundTrip(0, true)
        ), test.result!!.RoundTrips.toTypedArray())
    }

    @Test
    fun testAuthorizeFail() {
        setup(authResponse = MockResponse().setResponseCode(403))
        val test = LatencyTest(msakServer, latencyPort = serverPort, duration = 50)
        test.start()
        runBlocking { while (!test.updatesChan.receiveCatching().isClosed); }

        assertNull(test.result)
        assertNotNull(test.error)

        // make sure no UDP packets were sent
        val buf = ByteArray(1024)
        val pkt = DatagramPacket(buf, buf.size)
        assertThrows(SocketTimeoutException::class.java) { socket.receive(pkt) }
    }

    @Test
    fun testInitialPacketRetries() {
        setup()
        val test = LatencyTest(msakServer, latencyPort = serverPort, duration = 100, retryDelay = 10L, retryBackoff = 10L)
        test.start()

        server.takeRequest(100, TimeUnit.MILLISECONDS) // auth
        expectPacket(LatencyMessage("c2s", "fakeid", 0, null))
        expectPacket(LatencyMessage("c2s", "fakeid", 0, null))

        sendPacket(LatencyMessage("s2c", "fakeid", 1, null))
        expectPacket(LatencyMessage("s2c", "fakeid", 1, null))

        runBlocking { while (!test.updatesChan.receiveCatching().isClosed); }
    }

    @Test
    fun testInitialPacketNoResponse() {
        setup(resultResponse = MockResponse().setBody("""{
            "ID": "fakeid",
            "StartTime": "2023-11-30T14:48:20.123Z",
            "RoundTrips": [{"Lost": true}],
            "PacketsSent": 1,
            "PacketsReceived": 0
        }""".trimIndent()))

        val test = LatencyTest(msakServer, latencyPort = serverPort, duration = 100, retryDelay = 10L, retryBackoff = 10L)
        test.start()

        server.takeRequest(100, TimeUnit.MILLISECONDS) // auth
        expectPacket(LatencyMessage("c2s", "fakeid", 0, null))
        expectPacket(LatencyMessage("c2s", "fakeid", 0, null))
        expectPacket(LatencyMessage("c2s", "fakeid", 0, null))

        runBlocking { while (!test.updatesChan.receiveCatching().isClosed); }

        assertNotNull(test.error)
        assertEquals(1, test.result?.PacketsSent)
        assertEquals(0, test.result?.PacketsReceived)

        // make sure no more UDP packets were sent
        val buf = ByteArray(1024)
        val pkt = DatagramPacket(buf, buf.size)
        assertThrows(SocketTimeoutException::class.java) { socket.receive(pkt) }
    }

    @Test
    fun testResultFail() {
        setup(resultResponse = MockResponse().setResponseCode(500))
        val test = LatencyTest(msakServer, latencyPort = serverPort, duration = 50)
        test.start()

        server.takeRequest(100, TimeUnit.MILLISECONDS) // auth

        expectPacket(LatencyMessage("c2s", "fakeid", 0, null))

        sendPacket(LatencyMessage("s2c", "fakeid", 1, null))
        expectPacket(LatencyMessage("s2c", "fakeid", 1, null))

        sendPacket(LatencyMessage("s2c", "fakeid", 2, 10))
        expectPacket(LatencyMessage("s2c", "fakeid", 2, 10))

        sendPacket(LatencyMessage("s2c", "fakeid", 3, 20))
        expectPacket(LatencyMessage("s2c", "fakeid", 3, 20))

        runBlocking { while (!test.updatesChan.receiveCatching().isClosed); }

        server.takeRequest(100, TimeUnit.MILLISECONDS) // result

        assertNull(test.result)
        assertNotNull(test.error)
    }

    @Test
    fun testMalformedAuthorize() {
        setup(authResponse = MockResponse().setBody("Not JSON!"))
        val test = LatencyTest(msakServer, latencyPort = serverPort, duration = 50)
        test.start()
        runBlocking { while (!test.updatesChan.receiveCatching().isClosed); }
        assertNull(test.result)
        assertNotNull(test.error)
    }

    @Test
    fun testMalformedResult() {
        setup(resultResponse = MockResponse().setBody("Not JSON!"))
        val test = LatencyTest(msakServer, latencyPort = serverPort, duration = 50)
        test.start()
        runBlocking { while (!test.updatesChan.receiveCatching().isClosed); }
        assertNull(test.result)
        assertNotNull(test.error)
    }
}
