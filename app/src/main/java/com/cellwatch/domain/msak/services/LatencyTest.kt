package com.cellwatch.domain.msak.services

import android.util.Log
import com.cellwatch.domain.msak.model.LatencyMessage
import com.cellwatch.domain.msak.model.LatencyResult
import com.cellwatch.domain.msak.model.LatencyResultMessage
import okhttp3.OkHttpClient
import com.cellwatch.domain.msak.model.LocateServer
import com.cellwatch.domain.msak.util.LATENCY_CHARSET
import com.cellwatch.domain.msak.util.LATENCY_DURATION
import com.cellwatch.domain.msak.util.LATENCY_END_DELAY
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.concurrent.thread
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
    // TODO: make the LocateManager create these URLs once they're in the locate response
    private val authorizeUrl = "http://10.0.2.2:8080/latency/v1/authorize?mid=${measurementId}"
    private val resultsUrl = "http://10.0.2.2:8080/latency/v1/result?mid=${measurementId}"
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

    private suspend fun echoPackets(initialMessage: LatencyMessage) {
        val sock = DatagramSocket()
        var closing = false

        val echoThread = thread {
            val serverAddr = InetAddress.getByName(server.machine)
            val serverPort = 1053 // TODO: don't hardcode this

            val buf = ByteArray(1024)
            val initialMessageBytes = Gson().toJson(initialMessage).toByteArray(LATENCY_CHARSET)
            initialMessageBytes.forEachIndexed { i, b -> buf[i] = b }

            val pkt = DatagramPacket(buf, initialMessageBytes.size)
            pkt.address = serverAddr
            pkt.port = serverPort

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

        delay(LATENCY_DURATION + LATENCY_END_DELAY)

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
                    true,
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