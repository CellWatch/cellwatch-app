package com.cellwatch.domain.msak.services

import java.net.InetAddress
import java.net.Socket
import javax.net.SocketFactory

class ThroughputSocketFactory: SocketFactory() {
    private val socks = ArrayList<ThroughputSocket>()
    val throughputSock: ThroughputSocket?
        // use the last created socket -- first may be for DNS
        get() = socks.getOrNull(socks.size - 1)

    override fun createSocket(): Socket {
        val sock = ThroughputSocket()
        socks.add(sock)
        return sock
    }

    // OkHTTP docs claim to only use the parameter-less constructor above -- leave the rest
    // un-implemented

    override fun createSocket(host: String?, port: Int): Socket {
        TODO("Not yet implemented")
    }

    override fun createSocket(
        host: String?,
        port: Int,
        localHost: InetAddress?,
        localPort: Int
    ): Socket {
        TODO("Not yet implemented")
    }

    override fun createSocket(host: InetAddress?, port: Int): Socket {
        TODO("Not yet implemented")
    }

    override fun createSocket(
        address: InetAddress?,
        port: Int,
        localAddress: InetAddress?,
        localPort: Int
    ): Socket {
        TODO("Not yet implemented")
    }
}