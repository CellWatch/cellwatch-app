package edu.gatech.cc.cellwatch.data.network

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread

class DeviceRegistrationTest {
    private class FakeCredentials(var secret: String? = null, var registered: Boolean = false) : DeviceCredentials {
        override suspend fun secret() = secret
        override suspend fun saveSecret(secret: String) { this.secret = secret }
        override suspend fun isRegistered() = registered
        override suspend fun markRegistered() { registered = true }
    }

    @Test
    fun aNewPhoneSavesItsSecretBeforeRegistering() = runBlocking<Unit> {
        val credentials = FakeCredentials()
        var savedWhenRegistering: String? = null

        val secret = registeredDeviceSecret(credentials, newSecret = { "made on the phone" }) {
            savedWhenRegistering = credentials.secret
        }

        assertEquals("made on the phone", secret)
        assertEquals("the secret is saved before the server is asked", "made on the phone", savedWhenRegistering)
        assertTrue(credentials.registered)
    }

    @Test
    fun aLostResponseIsRetriedWithTheSameSecret() = runBlocking<Unit> {
        val credentials = FakeCredentials()
        val sent = mutableListOf<String>()
        var made = 0
        val newSecret = { "secret ${++made}" }

        assertThrows(IOException::class.java) {
            runBlocking { registeredDeviceSecret(credentials, newSecret) { sent += it; throw IOException("timed out") } }
        }
        assertFalse(credentials.registered)

        val secret = registeredDeviceSecret(credentials, newSecret) { sent += it }

        assertEquals(listOf("secret 1", "secret 1"), sent)
        assertEquals("secret 1", secret)
        assertTrue(credentials.registered)
    }

    @Test
    fun aRegisteredPhoneDoesNotRegisterAgain() = runBlocking<Unit> {
        val credentials = FakeCredentials(secret = "saved", registered = true)

        val secret = registeredDeviceSecret(credentials, newSecret = { "new" }) { fail("registered again") }

        assertEquals("saved", secret)
    }

    @Test
    fun aPhoneRegisteredByAnOlderVersionConfirmsItsSecretOnce() = runBlocking<Unit> {
        val credentials = FakeCredentials(secret = "made by the server")
        val sent = mutableListOf<String>()

        registeredDeviceSecret(credentials, newSecret = { "new" }) { sent += it }
        registeredDeviceSecret(credentials, newSecret = { "new" }) { sent += it }

        assertEquals(listOf("made by the server"), sent)
        assertEquals("made by the server", credentials.secret)
    }

    @Test
    fun newSecretsAre64HexDigitsAndDiffer() {
        val secret = newDeviceSecret()

        assertTrue(secret, secret.matches(Regex("[0-9a-f]{64}")))
        assertNotEquals(secret, newDeviceSecret())
    }

    @Test(timeout = 15_000)
    fun registrationSendsTheIdAndSecretAndAcceptsAnEmptyAnswer() {
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
            var request = ""
            val answer = thread {
                server.accept().use { socket ->
                    val input = socket.getInputStream().bufferedReader()
                    val head = generateSequence { input.readLine() }.takeWhile { it.isNotEmpty() }.toList()
                    val length = head.first { it.startsWith("Content-Length:", ignoreCase = true) }
                        .substringAfter(":").trim().toInt()
                    val body = CharArray(length)
                    var read = 0
                    while (read < length) {
                        read += input.read(body, read, length - read)
                    }
                    request = head.first() + "\n" + String(body)
                    // PostgREST answers a call to a function that returns void this way.
                    socket.getOutputStream().write("HTTP/1.1 204 No Content\r\nConnection: close\r\n\r\n".toByteArray())
                }
            }
            val client = createSupabaseClient("http://127.0.0.1:${server.localPort}", "test-key") {
                install(Postgrest)
            }
            try {
                runBlocking { requestDeviceRegistration(client, "device 1", "secret 1") }
            } finally {
                runBlocking { client.close() }
            }
            answer.join()

            assertTrue(request, request.startsWith("POST /rest/v1/rpc/register_device "))
            assertTrue(request, request.endsWith("""{"device_id":"device 1","device_secret":"secret 1"}"""))
        }
    }
}
