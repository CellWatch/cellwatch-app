package com.cellwatch.domain.msak.managers

import android.util.Log
import com.cellwatch.domain.msak.model.LatencyUrlType
import com.cellwatch.domain.msak.model.LocateResponse
import com.cellwatch.domain.msak.model.LocateServer
import com.cellwatch.domain.msak.model.ThroughputTestDirection
import com.cellwatch.domain.msak.util.THROUGHPUT_MAX_MILLIS
import com.cellwatch.domain.msak.util.THROUGHPUT_STREAMS
import com.cellwatch.domain.msak.util.THROUGHPUT_STREAM_DELAY
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.spectrum.android.ping.Ping
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

object LocateManager {
    private const val TAG = "MsakLocateManager"
    private const val locateUrl = "https://locate.measurementlab.net/v2/nearest/"

    suspend fun selectServerAsync(client: OkHttpClient): LocateServer {
        val throughputServers = getServers(client, "msak/throughput1")

        if (throughputServers.isEmpty()) {
            Log.e(TAG, "no throughput servers")
            throw Throwable("no servers found")
        }

        val server =  try {
            runBlocking { throughputServers.maxBy { ping(it.machine) } }
        } catch (t: Throwable) {
            Log.e(TAG, "pinging available servers failed", t)
            throughputServers[0]
        }

        val site = Regex("([^-]+)\\.").find(server.machine)?.groupValues?.get(1) ?: throw Throwable("not site found in machine ${server.machine}")
        val latencyServers = getServers(client, "msak/latency1", site)

        if (latencyServers.isEmpty()) {
            Log.e(TAG, "no latency servers at site $site")
            throw Throwable("no servers found")
        }

        latencyServers[0].urls.forEach { k, v -> server.urls[k] = v }
        return server
    }

    private suspend fun getServers(
        client: OkHttpClient,
        test: String,
        site: String? = null,
    ): List<LocateServer> = suspendCoroutine { continuation ->
        val request = Request.Builder().url("${locateUrl}${test}${if (site != null) { "?site=$site" } else { "" }}").build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWithException(e) // resume calling coroutine
                e.printStackTrace()
            }

            override fun onResponse(call: Call, response: Response) {
                val body = response.body
                if (response.code != 200 || body == null) {
                    Log.e(TAG, "locate request $request failed: $response")
                    continuation.resumeWithException(Throwable("locate request $request failed: $response"))
                    return
                }

                val results = try {
                    Gson().fromJson(body.charStream(), LocateResponse::class.java).results
                } catch (e: JsonSyntaxException) {
                    Log.e(TAG, "locate response deserialization failed: $body", e)
                    continuation.resumeWithException(e)
                    return
                }

                Log.d(TAG, "got ${results.size} results: $results")
                if (results.isEmpty()) {
                    Log.e(TAG, "locate request $request returned no servers: $response")
                    continuation.resumeWithException(Throwable("no servers found"))
                    return
                }

                continuation.resume(results)
            }
        })
    }

    suspend fun ping(host: String, count: Int = 5, delayMillis: Int = 5): Double {
        val timeChan = Channel<Long>(count)

        // TODO: can we detect whether IPv6 is going to work and not prefer IPv4?
        val addrs = withContext(Dispatchers.IO) { InetAddress.getAllByName(host) }
        val addrs4 = addrs.filterIsInstance<Inet4Address>()
        val addr = if (addrs4.isNotEmpty()) addrs4[0] else addrs.first()

        Log.d(TAG, "pinging $host (${addr.hostAddress})")
        val ping = Ping(addr, object: Ping.PingListener {
            override fun onPing(timeMillis: Long, index: Int) {
                Log.v(TAG, "got ping $index after ${timeMillis}ms")
                runBlocking { timeChan.send(timeMillis) }
            }

            override fun onPingException(e: Exception?, count: Int) {
                timeChan.close(e)
            }
        })

        ping.count = count
        ping.delayMs = delayMillis
        withContext(Dispatchers.IO) { ping.run() }

        var totalTimeMillis = 0L
        repeat(count) { totalTimeMillis += timeChan.receive() }

        Log.d(TAG, "ping results: ${totalTimeMillis / count}ms")
        return totalTimeMillis.toDouble() / count.toDouble()
    }

    fun getThroughputUrl(
        server: LocateServer,
        direction: ThroughputTestDirection,
        measurementId: String?,
    ): String {
        val testUrl = "/throughput/v1/${if (direction == ThroughputTestDirection.DOWNLOAD) "download" else "upload" }"
        val baseUrl = server.urls["wss://$testUrl"] ?: server.urls["ws://$testUrl"] ?: throw Throwable("no base URL found in urls: ${server.urls}")

        var options = "streams=$THROUGHPUT_STREAMS&duration=$THROUGHPUT_MAX_MILLIS&delay=$THROUGHPUT_STREAM_DELAY"
        if (measurementId != null) {
            options += "&mid=$measurementId"
        }

        return "$baseUrl${if (baseUrl.contains("?")) "&" else "?"}$options"
    }

    fun getLatencyUrl(
        server: LocateServer,
        type: LatencyUrlType,
        measurementId: String?,
    ): String {
        val testUrl = "/latency/v1/${if (type == LatencyUrlType.AUTH) "authorize" else "result"}"
        val baseUrl = server.urls["https://$testUrl"] ?: server.urls["http://$testUrl"] ?: throw Throwable("no base URL found in urls: ${server.urls}")

        if (measurementId == null) {
            return baseUrl
        }

        return "$baseUrl${if (baseUrl.contains("?")) "&" else "?"}mid=$measurementId"
    }
}