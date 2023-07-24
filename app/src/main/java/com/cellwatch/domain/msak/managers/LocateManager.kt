package com.cellwatch.domain.msak.managers

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import com.cellwatch.CellWatchApp
import com.cellwatch.domain.msak.model.LocateResponse
import com.cellwatch.domain.msak.model.LocateServer
import com.cellwatch.domain.msak.model.MsakTestDirection
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
    private const val locateUrl = "https://locate-dot-mlab-staging.appspot.com/v2/nearest/" //"https://locate.measurementlab.net/v2/nearest/"

    suspend fun selectServerAsync(
        client: OkHttpClient,
        locateUrl: String = LocateManager.locateUrl
    ): LocateServer = suspendCoroutine { continuation ->
        val request = Request.Builder().url("${locateUrl}msak/throughput1").build()

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
                    val mHandler = Handler(Looper.getMainLooper())
                    mHandler.post(Runnable {
                        // Your UI updates here
                        Toast.makeText(CellWatchApp.applicationContext(), "Locate Request Failed with code ${response.code}", Toast.LENGTH_LONG).show()
                    })
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

                val locateServer: LocateServer = try {
                    runBlocking { results.maxBy { ping(it.machine) } }
                } catch (t: Throwable) {
                    Log.e(TAG, "pinging available servers failed", t)
                    results[0]
                }

                response.use {
                    continuation.resume(locateServer) // resume calling coroutine
                }
            }
        })
    }

    fun selectServer(
        client: OkHttpClient,
        locateUrl: String = LocateManager.locateUrl
    ): LocateServer {
        val request = Request.Builder().url("${locateUrl}msak/msak").build()
        val response = try {
            client.newCall(request).execute()
        } catch (t: Throwable) {
            Log.e(TAG, "locate request $request threw error", t)
            throw t
        }

        val body = response.body
        if (response.code != 200 || body == null) {
            Log.e(TAG, "locate request $request failed: $response")
            throw Throwable("locate request $request failed: $response")
        }

        val results = try {
            Gson().fromJson(body.charStream(), LocateResponse::class.java).results
        } catch (e: JsonSyntaxException) {
            Log.e(TAG, "locate response deserialization failed: $body", e)
            throw e
        }

        Log.d(TAG, "got ${results.size} results: $results")
        if (results.isEmpty()) {
            Log.e(TAG, "locate request $request returned no servers: $response")
            throw Throwable("no servers found")
        }

        return try {
            runBlocking { results.maxBy { ping(it.machine) } }
        } catch (t: Throwable) {
            Log.e(TAG, "pinging available servers failed", t)
            results[0]
        }
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
        direction: MsakTestDirection,
        measurementId: String?,
    ): String {
        val testUrl = "/throughput/v1/${if (direction == MsakTestDirection.DOWNLOAD) "download" else "upload" }"
        val baseUrl = server.urls["wss://$testUrl"] ?: server.urls["ws://$testUrl"] ?: throw Throwable("no base URL found in urls: $server.urls")

        var options = "streams=$THROUGHPUT_STREAMS&duration=$THROUGHPUT_MAX_MILLIS&delay=$THROUGHPUT_STREAM_DELAY"
        if (measurementId != null) {
            options += "&mid=$measurementId"
        }

        return "$baseUrl${if (baseUrl.contains("?")) "&" else "?"}$options"
    }
}