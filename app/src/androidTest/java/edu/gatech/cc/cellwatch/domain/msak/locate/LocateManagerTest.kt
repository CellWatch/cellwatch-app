package edu.gatech.cc.cellwatch.domain.msak.locate

import androidx.test.ext.junit.runners.AndroidJUnit4
import edu.gatech.cc.cellwatch.domain.msak.LOCATE_LATENCY_PATH
import edu.gatech.cc.cellwatch.domain.msak.LOCATE_THROUGHPUT_PATH
import edu.gatech.cc.cellwatch.domain.msak.Server
import edu.gatech.cc.cellwatch.domain.msak.ServerLocation
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class LocateManagerTest {
    private lateinit var server: MockWebServer
    private lateinit var manager: LocateManager

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
        manager = LocateManager(locateUrl = server.url("/").toString())
    }

    fun makeServers(num: Int): List<Server> {
        return List(num) {
            Server("mlab1-iad$it.mlab-oti.example.com", ServerLocation("ATL$it", "US"), mapOf(
                "wss:///t/test" to "wss://msak-mlab1-iad$it.map-oti.example.com/t/test",
                "https:///l/test" to "https://msak-mlab1-iad$it.map-oti.example.com/l/test"
            ))
        }
    }

    @Test
    fun testLocateThroughput() {
        val response = LocateResponse(makeServers(3))
        server.enqueue(MockResponse().setBody(Gson().toJson(response)))

        val located = runBlocking { manager.locateThroughputServers() }
        assertEquals(response.results, located)

        val req = server.takeRequest(0, TimeUnit.MILLISECONDS)
        assertEquals("/$LOCATE_THROUGHPUT_PATH", req?.path)
    }

    @Test
    fun testLocateThroughputWithServer() {
        val response = LocateResponse(makeServers(3))
        server.enqueue(MockResponse().setBody(Gson().toJson(response)))

        val located = runBlocking { manager.locateThroughputServers(response.results[1]) }
        assertEquals(response.results, located)

        val req = server.takeRequest(0, TimeUnit.MILLISECONDS)
        assertEquals("/$LOCATE_THROUGHPUT_PATH?site=iad1", req?.path)
    }

    @Test
    fun testLocateThroughputFail() {
        server.enqueue(MockResponse().setResponseCode(400))

        assertThrows(Throwable::class.java) {
            runBlocking { manager.locateThroughputServers() }
        }
    }

    @Test
    fun testLocateThroughputMalformed() {
        server.enqueue(MockResponse().setBody("not JSON!"))

        assertThrows(Throwable::class.java) {
            runBlocking { manager.locateThroughputServers() }
        }
    }

    @Test
    fun testLocateLatency() {
        val response = LocateResponse(makeServers(3))
        server.enqueue(MockResponse().setBody(Gson().toJson(response)))

        val located = runBlocking { manager.locateLatencyServers() }
        assertEquals(response.results, located)

        val req = server.takeRequest(0, TimeUnit.MILLISECONDS)
        assertEquals("/$LOCATE_LATENCY_PATH", req?.path)
    }

    @Test
    fun testLocateLatencyWithServer() {
        val response = LocateResponse(makeServers(3))
        server.enqueue(MockResponse().setBody(Gson().toJson(response)))

        val located = runBlocking { manager.locateLatencyServers(response.results[1]) }
        assertEquals(response.results, located)

        val req = server.takeRequest(0, TimeUnit.MILLISECONDS)
        assertEquals("/$LOCATE_LATENCY_PATH?site=iad1", req?.path)
    }

    @Test
    fun testLocateLatencyFail() {
        server.enqueue(MockResponse().setResponseCode(400))

        assertThrows(Throwable::class.java) {
            runBlocking { manager.locateLatencyServers() }
        }
    }

    @Test
    fun testLocateLatencyMalformed() {
        server.enqueue(MockResponse().setBody("not JSON!"))

        assertThrows(Throwable::class.java) {
            runBlocking { manager.locateLatencyServers() }
        }
    }
}
