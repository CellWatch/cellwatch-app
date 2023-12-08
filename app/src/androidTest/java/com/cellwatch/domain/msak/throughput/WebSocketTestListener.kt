package com.cellwatch.domain.msak.throughput

import com.google.gson.Gson
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString

class WebSocketTestListener: WebSocketListener() {
    var opened = false; private set
    var closingCode: Int? = null; private set
    val closing; get() = closingCode != null
    var closeCode: Int? = null; private set
    val closed; get() = closeCode != null
    var error: Throwable? = null; private set
    val failed; get() = error != null

    private lateinit var webSocket: WebSocket
    private val openChan  = Channel<Boolean>(1)
    private val closingChan = Channel<Boolean>(1)
    private val closedChan = Channel<Boolean>(1)
    private val failedChan = Channel<Boolean>(1)
    private val textMessages = Channel<String>(1024)
    private val byteMessages = Channel<ByteString>(1024)

    fun send(text: String) {
        if (!opened || closed || failed) throw Throwable("not open")
        webSocket.send(text)
    }

    fun send(bytes: ByteString) {
        if (!opened || closed || failed) throw Throwable("not open")
        webSocket.send(bytes)
    }

    fun sendMeasurement(
        appBytesSent: Long,
        appBytesRecv: Long,
        time: Long,
        netBytesSent: Long? = null,
        netBytesRecv: Long? = null,
        cc: String? = null,
        uuid: String? = null,
        localAddr: String? = null,
        remoteAddr: String? = null,
    ): Int {
        val measurement = ThroughputMeasurement(
            if (netBytesSent != null || netBytesRecv != null) {
                ByteCounters(netBytesSent ?: 0, netBytesRecv ?: 0)
            } else {
                null
            },
            ByteCounters(appBytesSent, appBytesRecv),
            time,
            cc,
            uuid,
            localAddr,
            remoteAddr
        )

        val text = Gson().toJson(measurement)
        send(text)
        return text.length
    }

    fun sendBytes(num: Int): Int {
        send(ByteArray(num).toByteString())
        return num
    }

    fun close(code: Int) {
        if (!opened || closed || failed) throw Throwable("not open")
        webSocket.close(code, "")
    }

    suspend fun takeTextMessage(timeoutMillis: Long): String? {
        return withTimeoutOrNull(timeoutMillis) { textMessages.receive() }
    }

    suspend fun takeByteMessage(timeoutMillis: Long): ByteString? {
        return withTimeoutOrNull(timeoutMillis) { byteMessages.receive() }
    }

    suspend fun takeMeasurement(timeoutMillis: Long): ThroughputMeasurement? {
        val text = takeTextMessage(timeoutMillis)
        return if (text == null) null else Gson().fromJson(text, ThroughputMeasurement::class.java)
    }

    suspend fun waitOpened(timeoutMillis: Long): Boolean {
        return withTimeoutOrNull(timeoutMillis) { openChan.receive() } ?: false
    }

    suspend fun waitClosing(timeoutMillis: Long): Boolean {
        return withTimeoutOrNull(timeoutMillis) { closingChan.receive() } ?: false
    }

    suspend fun waitClosed(timeoutMillis: Long): Boolean {
        return withTimeoutOrNull(timeoutMillis) { closedChan.receive() } ?: false
    }

    suspend fun waitFailed(timeoutMillis: Long): Boolean {
        return withTimeoutOrNull(timeoutMillis) { failedChan.receive() } ?: false
    }

    override fun onOpen(webSocket: WebSocket, response: Response) {
        super.onOpen(webSocket, response)
        this.webSocket = webSocket
        opened = true
        runBlocking { openChan.send(true) }
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        super.onMessage(webSocket, text)
        runBlocking { textMessages.send(text) }
    }

    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
        super.onMessage(webSocket, bytes)
        runBlocking { byteMessages.send(bytes) }
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        super.onClosing(webSocket, code, reason)
        closingCode = code
        runBlocking { closingChan.send(true) }
        webSocket.close(1000, "close requested")
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        super.onClosed(webSocket, code, reason)
        closeCode = code
        runBlocking { closedChan.send(true) }
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        super.onFailure(webSocket, t, response)
        error = t
        runBlocking { failedChan.send(true) }
    }
}