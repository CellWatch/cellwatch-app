package edu.gatech.cc.cellwatch.data.network

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.Duration.Companion.seconds

class AllowSlowRequestsTest {
    // Never accepted, so requests connect but are never answered, like a server stuck on a query.
    private val server = ServerSocket(0, 10, InetAddress.getByName("127.0.0.1"))

    @After
    fun stopServer() = server.close()

    @Test(timeout = 15_000)
    fun abandonsARequestThatOutlastsTheTimeout() {
        val client = createSupabaseClient("http://127.0.0.1:${server.localPort}", "test-key") {
            install(Postgrest)
            allowSlowRequests(200.milliseconds)
        }
        try {
            val started = System.nanoTime()
            assertThrows(HttpRequestTimeoutException::class.java) {
                runBlocking { client.postgrest.rpc("ping") }
            }

            // supabase-kt's default would have waited 10 s.
            val waited = (System.nanoTime() - started).nanoseconds
            assertTrue("gave up after $waited", waited < 5.seconds)
        } finally {
            runBlocking { client.close() }
        }
    }
}
