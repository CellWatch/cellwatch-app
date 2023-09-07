package com.cellwatch.domain.msak.latency

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.cellwatch.BuildConfig
import com.cellwatch.domain.msak.LATENCY_CHARSET
import com.cellwatch.domain.msak.LATENCY_DURATION
import com.cellwatch.domain.msak.Server
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import io.ktor.http.Url
import io.ktor.util.reflect.instanceOf
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import kotlin.concurrent.thread
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class LatencyTest(
    private val server: Server,
    client: OkHttpClient? = null,
    measurementId: String? = null
) {
    private val TAG = this::class.simpleName
    private val latencyPort = BuildConfig.MSAK_LATENCY_PORT
    private val authorizeUrl = server.getLatencyAuthorizeUrl(measurementId)
    private val resultUrl = server.getLatencyResultUrl(measurementId)
    private val _updatesChan = Channel<LatencyUpdate>(32)
    private val _updates = ArrayList<LatencyUpdate>()
    private val client = client ?: OkHttpClient.Builder().build()
    private val socket = DatagramSocket()
    private val handler = Handler(Looper.getMainLooper())

    val updatesChan: ReceiveChannel<LatencyUpdate> = _updatesChan
    val updates: List<LatencyUpdate> = _updates
    var startTime: Instant? = null; private set
    var endTime: Instant? = null; private set
    var started = false; private set
    val ended; get() = endTime != null
    val serverHost = Url(authorizeUrl).host
    var result: LatencyResult? = null
    var error: Throwable? = null

    fun start() {
        if (started) {
            throw Throwable("already started")
        }

        started = true
        thread {
            try {
                runBlocking { run() }
            } catch (e: Exception) {
                Log.e(TAG, "latency test error", e)
                error = e
                finish()
            }
        }
    }

    fun stop() {
        if (!started) {
            throw Throwable("can't stop before starting")
        }

        finish()
    }

    private suspend fun run() {
        val initialMessage = authorize()
        Log.d(TAG, "got initial latency message: $initialMessage")

        val serverAddr = getServerAddr()
        Log.d(TAG, "using latency address $serverAddr for ${server.machine}")

        echoPackets(serverAddr, initialMessage)

        result = getResult()
        Log.d(TAG, "got latency result: $result")
        _updatesChan.close()
    }

    private fun finish(closeUpdatesChan: Boolean = true) {
        if (ended) {
            return
        }

        endTime = Clock.System.now()
        handler.removeCallbacksAndMessages(null)
        socket.close()
        if (closeUpdatesChan) {
            _updatesChan.close()
        }
    }

    private fun recordUpdate(update: LatencyUpdate) {
        val result = _updatesChan.trySend(update)
        if (!result.isSuccess) {
            Log.d(TAG, "failed to send latency message on channel: $result")
        }

        if (!result.isClosed) {
            _updates.add(update)
        }
    }

    private suspend fun authorize(): LatencyMessage = suspendCoroutine { continuation ->
        Log.d(TAG, "making authorize request to $authorizeUrl")
        val request = Request.Builder()
            .url(authorizeUrl)
            .header("User-Agent", BuildConfig.USER_AGENT)
            .build()

        client.newCall(request).enqueue(object: Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body
                if (response.code != 200 || body == null) {
                    Log.e(TAG, "authorize request $request failed: $response")
                    continuation.resumeWithException(Throwable("authorize request $request failed: $response"))
                    return
                }

                val initialMessage = try {
                    Gson().fromJson(body.charStream(), LatencyMessage::class.java)
                } catch (e: JsonSyntaxException) {
                    Log.e(TAG, "authorize response deserialization failed: $body", e)
                    continuation.resumeWithException(e)
                    return
                }

                continuation.resume(initialMessage)
            }
        })
    }

   private fun getServerAddr(): InetAddress {
       try {
           val addrs = InetAddress.getAllByName(serverHost)
           Log.d(TAG, "got latency addrs ${addrs.joinToString(", ")}")
           val v4Addrs = addrs.filter { it.instanceOf(Inet4Address::class) }
           // prefer IPv4 as IPv6 connectivity is often incomplete
           return if (v4Addrs.isNotEmpty()) v4Addrs[0] else addrs[0]
       } catch (t: Throwable) {
           Log.e(TAG, "no server addr for latency test", t)
           throw Throwable("no addr")
       }
   }

    private fun echoPackets(serverAddr: InetAddress, initialMessage: LatencyMessage) {
        val initialBuf = ByteArray(1024)
        val initialMessageBytes = Gson().toJson(initialMessage).toByteArray(LATENCY_CHARSET)
        initialMessageBytes.forEachIndexed { i, b -> initialBuf[i] = b }

        val initialPkt = DatagramPacket(initialBuf, initialMessageBytes.size)
        initialPkt.address = serverAddr
        initialPkt.port = latencyPort

        var gotOne = false
        fun sendInitial(maxRetries: Int) {
            if (gotOne) {
                return
            }
            socket.send(initialPkt)
            if (maxRetries > 0) {
                handler.postDelayed({
                    try {
                        sendInitial(maxRetries - 1)
                    } catch (e: Exception) {
                        error = e
                    }
                }, 1000L)
            }
        }

        startTime = Clock.System.now()
        sendInitial(2)

        val buf = ByteArray(1024)
        val pkt = DatagramPacket(buf, buf.size)
        while (true) {
            pkt.length = buf.size
            try {
                socket.receive(pkt)
            } catch (t: Throwable) {
                if (ended) {
                    break
                }

                Log.e(TAG, "failed to receive packet", t)
                throw t
            }
            val time = Clock.System.now()

            if (pkt.address != serverAddr || pkt.port != latencyPort) {
                Log.w(TAG, "received packet from unexpected sender: ${pkt.address}:${pkt.port}")
                continue
            }

            if (!gotOne) {
                gotOne = true
                handler.postDelayed({ finish(false) }, LATENCY_DURATION + 1000L)
            }

            val payload = buf.sliceArray(IntRange(0, pkt.length - 1)).toString(LATENCY_CHARSET)
            Log.v(TAG, "received packet: $payload")

            try {
                socket.send(pkt)
            } catch (t: Throwable) {
                if (ended) {
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

            recordUpdate(LatencyUpdate(time, message))
        }
    }

    private suspend fun getResult(): LatencyResult = suspendCoroutine { continuation ->
        val request = Request.Builder().url(resultUrl).build()

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
                val result = try {
                    Gson().fromJson(bodyStr, LatencyResult::class.java)
                } catch (e: JsonSyntaxException) {
                    Log.e(TAG, "results response deserialization failed: $bodyStr", e)
                    continuation.resumeWithException(e)
                    return
                }

                continuation.resume(result)
            }
        })
    }
}