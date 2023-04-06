package com.example.ndt8

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
): Ndt8LocateServer {
    val request = Request.Builder().url("${locateUrl}msak/ndt8").build()
    val response = try {
        client.newCall(request).execute()
    } catch (t: Throwable) {
        Log.e("Ndt8ServerSelection", "locate request $request threw error", t)
        throw t
    }

    val body = response.body
    if (response.code != 200 || body == null) {
        Log.e("Ndt8ServerSelection", "locate request $request failed: $response")
        throw Throwable("locate request failed")
    }

    val results = try {
        Gson().fromJson(body.charStream(), Ndt8LocateResponse::class.java).results
    } catch (e: JsonSyntaxException) {
        Log.e("Ndt8ServerSelection", "locate response deserialization failed: $body", e)
        throw e
    }

    Log.d("Ndt8ServerSelection", "got ${results.size} results: $results")
    if (results.isEmpty()) {
        Log.e("Ndt8ServerSelection", "locate request $request returned no servers: $response")
        throw Throwable("no servers found")
    }

    return try {
        runBlocking { results.maxBy { ping(it.machine) } }
    } catch (t: Throwable) {
        Log.e("Ndt8ServerSelection", "pinging available servers failed", t)
        results[0]
    }
}

suspend fun ping(host: String, count: Int = 5, delayMillis: Int = 5): Double {
    val timeChan = Channel<Long>(count)

    // TODO: can we detect whether IPv6 is going to work and not prefer IPv4?
    val addrs = withContext(Dispatchers.IO) { InetAddress.getAllByName(host) }
    val addrs4 = addrs.filterIsInstance<Inet4Address>()
    val addr = if (addrs4.isNotEmpty()) addrs4[0] else addrs.first()

    Log.d("Ndt8ServerSelection", "pinging $host (${addr.hostAddress})")
    val ping = Ping(addr, object: PingListener {
        override fun onPing(timeMillis: Long, index: Int) {
            Log.v("Ndt8ServerSelection", "got ping $index after ${timeMillis}ms")
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

    Log.d("Ndt8ServerSelection", "ping results: ${totalTimeMillis / count}ms")
    return totalTimeMillis.toDouble() / count.toDouble()
}

fun getUrl(
    server: Ndt8LocateServer,
    direction: Ndt8TestDirection,
    measurementId: String?,
): String {
    val testUrl = "/ndt/v8/${if (direction == Ndt8TestDirection.DOWNLOAD) "download" else "upload" }"
    val baseUrl = server.urls["wss://$testUrl"] ?: server.urls["ws://$testUrl"] ?: throw Throwable("no base URL found in urls: $server.urls")

    // TODO: figure out what units are expected for duration/delay
    var options = "streams=$NDT8_STREAMS&duration=${NDT8_MAX_MILLIS/1000}&delay=${NDT8_STREAM_DELAY/1000}"
    if (measurementId != null) {
        options += "&mid=$measurementId"
    }

    return "$baseUrl${if (baseUrl.contains("?")) "&" else "?"}$options"
}

data class Ndt8LocateResponse(val results: List<Ndt8LocateServer>)

data class Ndt8LocateServer(
    val machine: String,
    val location: Ndt8LocateServerLocation?,
    val urls: Map<String, String>,
)

data class Ndt8LocateServerLocation(
    val city: String?,
    val country: String?,
)