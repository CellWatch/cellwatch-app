package com.example.ndtm

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.spectrum.android.ping.Ping
import com.spectrum.android.ping.Ping.PingListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.Inet4Address
import java.net.InetAddress

fun selectServer(
    client: OkHttpClient,
    locateUrl: String = "https://locate.measurementlab.net/v2/nearest/"
): NdtMLocateServer {
    val request = Request.Builder().url("${locateUrl}msak/ndtm").build()
    val response = try {
        client.newCall(request).execute()
    } catch (t: Throwable) {
        Log.e("NdtMServerSelection", "locate request $request threw error", t)
        throw t
    }

    val body = response.body
    if (response.code != 200 || body == null) {
        Log.e("NdtMServerSelection", "locate request $request failed: $response")
        throw Throwable("locate request failed")
    }

    val results = try {
        Gson().fromJson(body.charStream(), NdtMLocateResponse::class.java).results
    } catch (e: JsonSyntaxException) {
        Log.e("NdtMServerSelection", "locate response deserialization failed: $body", e)
        throw e
    }

    Log.d("NdtMServerSelection", "got ${results.size} results: $results")
    if (results.isEmpty()) {
        Log.e("NdtMServerSelection", "locate request $request returned no servers: $response")
        throw Throwable("no servers found")
    }

    return try {
        runBlocking { results.maxBy { ping(it.machine) } }
    } catch (t: Throwable) {
        Log.e("NdtMServerSelection", "pinging available servers failed", t)
        results[0]
    }
}

suspend fun ping(host: String, count: Int = 5, delayMillis: Int = 5): Double {
    val timeChan = Channel<Long>(count)

    // TODO: can we detect whether IPv6 is going to work and not prefer IPv4?
    val addrs = withContext(Dispatchers.IO) { InetAddress.getAllByName(host) }
    val addrs4 = addrs.filterIsInstance<Inet4Address>()
    val addr = if (addrs4.isNotEmpty()) addrs4[0] else addrs.first()

    Log.d("NdtMServerSelection", "pinging $host (${addr.hostAddress})")
    val ping = Ping(addr, object: PingListener {
        override fun onPing(timeMillis: Long, index: Int) {
            Log.v("NdtMServerSelection", "got ping $index after ${timeMillis}ms")
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

    Log.d("NdtMServerSelection", "ping results: ${totalTimeMillis / count}ms")
    return totalTimeMillis.toDouble() / count.toDouble()
}

fun getUrl(
    server: NdtMLocateServer,
    direction: NdtMTestDirection,
    measurementId: String,
): String {
    val testUrl = "/msak/ndtm/${if (direction == NdtMTestDirection.DOWNLOAD) "download" else "upload" }"
    val baseUrl = server.urls["wss://$testUrl"] ?: server.urls["ws://$testUrl"] ?: throw Throwable("no base URL found in urls: $server.urls")
    return "$baseUrl${if (baseUrl.contains("?")) "&" else "?"}mid=$measurementId"
}

data class NdtMLocateResponse(val results: List<NdtMLocateServer>)

data class NdtMLocateServer(
    val machine: String,
    val location: NdtMLocateServerLocation?,
    val urls: Map<String, String>,
)

data class NdtMLocateServerLocation(
    val city: String?,
    val country: String?,
)