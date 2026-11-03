package edu.gatech.cc.cellwatch

import androidx.test.ext.junit.runners.AndroidJUnit4
import edu.gatech.cc.cellwatch.msak.throughput.CountableSocketFactory
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.net.InetAddress
import java.security.Security
import javax.net.ssl.SSLContext
import kotlin.concurrent.thread
import kotlin.random.Random

/**
 * Conscrypt, which the app installs as its first security provider (see CellWatchApp), so it does a
 * speed test's TLS. msak's CountableSocket counts the bytes the test moves, underneath that TLS.
 */
@RunWith(AndroidJUnit4::class)
class ConscryptTest {
    @Test
    fun tlsUsesConscrypt() {
        assertEquals("Conscrypt", Security.getProviders().first().name)
        assertEquals("Conscrypt", SSLContext.getInstance("TLS").provider.name)
    }

    @Test(timeout = 30_000)
    fun aCountableSocketCountsTheBytesOfATlsDownload() {
        val certificate = HeldCertificate.Builder().addSubjectAlternativeName("127.0.0.1").build()
        val serverCertificates = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val clientCertificates = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        val body = Random(1).nextBytes(256 * 1024)

        val server = serverCertificates.sslContext().serverSocketFactory
            .createServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val received = try {
            // An exception left uncaught on another thread would crash the whole test run. The
            // client's call fails instead.
            thread {
                runCatching {
                    server.accept().use { socket ->
                        val request = socket.getInputStream().bufferedReader()
                        while (!request.readLine().isNullOrEmpty()) continue
                        socket.getOutputStream().apply {
                            write("HTTP/1.1 200 OK\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n".toByteArray())
                            write(body)
                            flush()
                        }
                    }
                }
            }
            // As msak's ThroughputStream sets up its connection, but trusting the test certificate.
            val sockets = CountableSocketFactory()
            val client = OkHttpClient.Builder()
                .socketFactory(sockets)
                .sslSocketFactory(clientCertificates.sslSocketFactory(), clientCertificates.trustManager)
                .build()
            val response = client.newCall(Request.Builder().url("https://127.0.0.1:${server.localPort}/").build()).execute()
            response.use { it.body!!.bytes() } to sockets.sockets
        } finally {
            server.close()
        }

        val (bytes, sockets) = received
        assertArrayEquals(body, bytes)
        // What's counted includes TLS: the handshake, and a header and tag on every record.
        val counted = sockets.sumOf { it.inBytes }
        assertTrue("counted $counted bytes for a ${body.size}-byte body", counted in body.size..body.size + 16 * 1024)
        assertTrue(sockets.sumOf { it.outBytes } > 0)
    }

    @Test
    fun conscryptsLibrarySupports16KbPages() {
        val conscrypt = NativeLibraries.inInstalledApp().filter { it.name == "libconscrypt_jni.so" }

        assertTrue(conscrypt.isNotEmpty())
        assertEquals(emptyList<NativeLibraries.Library>(), conscrypt.filterNot { it.supports16KbPages })
    }
}
