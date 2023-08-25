package com.cellwatch.domain.msak.services

import org.apache.commons.io.input.CountingInputStream
import org.apache.commons.io.output.CountingOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket

class ThroughputSocket: Socket() {
    private var inStream: CountingInputStream? = null
    private var outStream: CountingOutputStream? = null

    val inBytes
        get() = inStream?.byteCount ?: 0
    val outBytes
        get() = outStream?.byteCount ?: 0

    override fun getInputStream(): InputStream {
        val i = inStream ?: CountingInputStream(super.getInputStream())
        inStream = i
        return i
    }

    override fun getOutputStream(): OutputStream {
        val o = outStream ?: CountingOutputStream(super.getOutputStream())
        outStream = o
        return o
    }
}