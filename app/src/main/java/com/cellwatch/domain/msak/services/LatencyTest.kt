package com.cellwatch.domain.msak.services

import android.util.Log
import com.cellwatch.domain.msak.managers.LocateManager
import com.cellwatch.domain.msak.model.LatencyMessage
import com.cellwatch.domain.msak.model.LatencyResult
import com.cellwatch.domain.msak.model.LatencyResultMessage
import com.cellwatch.domain.msak.model.LatencyUrlType
import okhttp3.OkHttpClient
import com.cellwatch.domain.msak.model.LocateServer
import com.cellwatch.domain.msak.util.LATENCY_CHARSET
import com.cellwatch.domain.msak.util.LATENCY_DURATION
import com.cellwatch.domain.msak.util.LATENCY_END_DELAY
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import io.ktor.util.reflect.instanceOf
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.CancellationException
import kotlin.concurrent.thread
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.math.abs

class LatencyTest (
    private val client: OkHttpClient,
    private val server: LocateServer,
    measurementId: String?,
) {
    private val TAG = LatencyTest::class.simpleName
    private val authorizeUrl = LocateManager.getLatencyUrl(server, LatencyUrlType.AUTH, measurementId)
    private val resultsUrl = LocateManager.getLatencyUrl(server, LatencyUrlType.RESULT, measurementId)
    private val serverHost = Regex("^https?://([^/]+)/").find(authorizeUrl)?.groupValues?.get(1) ?: throw Throwable("no hostname found in authorize URL $authorizeUrl")
    val progress = Channel<LatencyMessage>(32)

    suspend fun run(): LatencyResult {
        try {
            val initialMessage = authorize()
            echoPackets(initialMessage)
            return getResults()
        } catch (t: Throwable) {
            Log.e(TAG, "latency test failed", t)
            return LatencyResult(
                Clock.System.now(),
                0,
                false,
                0,
                0,
                0,
                0,
            )
        } finally {
            progress.close()
        }
    }

    private suspend fun authorize(): LatencyMessage = suspendCoroutine { continuation ->
        Log.d(TAG, "making authorize request to $authorizeUrl")
        val request = Request.Builder().url(authorizeUrl).build()

        client.newCall(request).enqueue(object: Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body
                if (response.code != 200 || body == null) {
                    Log.e(TAG, "authorize request $request failed: $response")
                    throw Throwable("authorize request $request failed: $response")
                }

                val initialMessage = try {
                    Gson().fromJson(body.charStream(), LatencyMessage::class.java)
                } catch (e: JsonSyntaxException) {
                    Log.e(TAG, "authorize response deserialization failed: $body", e)
                    throw e
                }

                Log.d(TAG, "got initial latency message: $initialMessage")
                continuation.resume(initialMessage)
            }
        })
    }

    private suspend fun echoPackets(initialMessage: LatencyMessage) = coroutineScope {
        val sock = DatagramSocket()
        var closing = false
        var delayJob: Job? = null

        fun echo() {
            val serverAddr = try {
                val addrs = InetAddress.getAllByName(serverHost)
                Log.d(TAG, "got latency addrs ${addrs.joinToString(", ")}")
                val v4Addrs = addrs.filter { it.instanceOf(Inet4Address::class) }
                // prefer IPv4 as IPv6 connectivity is often incomplete
                if (v4Addrs.isNotEmpty()) v4Addrs[0] else addrs[0]
            } catch (t: Throwable) {
                Log.e(TAG, "no server addr for latency test", t)
                throw Throwable("no addr")
            }

            Log.d(TAG, "using latency address $serverAddr for ${server.machine}")
            val serverPort = 1053 // TODO: don't hardcode this

            val buf = ByteArray(1024)
            val initialMessageBytes = Gson().toJson(initialMessage).toByteArray(LATENCY_CHARSET)
            initialMessageBytes.forEachIndexed { i, b -> buf[i] = b }

            val pkt = DatagramPacket(buf, initialMessageBytes.size)
            pkt.address = serverAddr
            pkt.port = serverPort

            // TODO: what if this initial packet doesn't make it?
            sock.send(pkt)

            while (true) {
                pkt.length = buf.size
                try {
                    sock.receive(pkt)
                } catch (t: Throwable) {
                    if (closing) {
                        break
                    }

                    Log.e(TAG, "failed to receive packet", t)
                    throw t
                }

                if (pkt.address != serverAddr || pkt.port != serverPort) {
                    Log.w(TAG, "received packet from unexpected sender: ${pkt.address}:${pkt.port}")
                    continue
                }

                val payload = buf.sliceArray(IntRange(0, pkt.length - 1)).toString(LATENCY_CHARSET)
                Log.d(TAG, "received packet: $payload")

                try {
                    sock.send(pkt)
                } catch (t: Throwable) {
                    if (closing) {
                        break
                    }

                    Log.e(TAG, "failed to send packet")
                    throw t
                }

                val message = try {
                    Gson().fromJson(payload, LatencyMessage::class.java)
                } catch (e: JsonSyntaxException) {
                    Log.e(TAG, "latency message deserialization failed: $payload", e)
                    continue
                }

                progress.trySend(message)
            }
        }

        val echoThread = thread {
            try {
                echo()
                delayJob?.cancel()
            } catch (e: Exception) {
                Log.e(TAG, "unexpected error echoing packets" , e)
                delayJob?.cancel(CancellationException("latency test failed"))
            }

        }

        delayJob = launch { delay(LATENCY_DURATION + LATENCY_END_DELAY) }
        delayJob.join()

        closing = true
        sock.close()
        echoThread.join(5000L)
        if (echoThread.isAlive) {
            Log.w(TAG, "echo thread did not die: $echoThread")
        }
    }

    private suspend fun getResults(): LatencyResult = suspendCoroutine { continuation ->
        val request = Request.Builder().url(resultsUrl).build()

        client.newCall(request).enqueue(object: Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body
                if (response.code != 200 || body == null) {
                    Log.e(TAG, "results request $request failed: $response")
                    continuation.resumeWithException(Throwable("results request $request failed: $response"))
                    return
                }

                val bodyStr = body.string()
                val resultMessage = try {
                    Gson().fromJson(bodyStr, LatencyResultMessage::class.java)
                } catch (e: JsonSyntaxException) {
                    Log.e(TAG, "results response deserialization failed: $bodyStr", e)
                    continuation.resumeWithException(e)
                    return
                }

                Log.d(TAG, "got latency result message: $resultMessage")

                val rtts = resultMessage.RoundTrips.filter { !it.Lost }.map {it.RTT}
                val meanRtt = rtts.average()
                val variance = rtts.map { abs(it - meanRtt) }.sum() / rtts.size

                val result = LatencyResult(
                    Instant.parse(resultMessage.StartTime),
                    LATENCY_DURATION * 1000,
                    resultMessage.PacketsSent > 0,
                    meanRtt.toInt(),
                    variance.toInt(),
                    resultMessage.PacketsSent,
                    resultMessage.PacketsReceived,
                )

                continuation.resume(result)
            }
        })
    }
}