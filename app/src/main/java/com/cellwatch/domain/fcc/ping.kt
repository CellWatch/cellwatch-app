package com.cellwatch.domain.fcc

import android.util.Log
import com.spectrum.android.ping.Ping
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.net.Inet4Address
import java.net.InetAddress

suspend fun ping(host: String, count: Int = 5, delayMillis: Int = 5): Double {
    val TAG = "ping"
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
