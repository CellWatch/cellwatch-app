package edu.gatech.cc.cellwatch.domain.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

internal actual suspend fun httpGetText(url: String, userAgent: String): String =
    withContext(Dispatchers.IO) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("User-Agent", userAgent)
            // Short: a submission must not stall on an unreachable service.
            connectTimeout = 5_000
            readTimeout = 5_000
        }
        try {
            val status = connection.responseCode
            if (status !in 200..299) {
                throw IllegalStateException("tcp tuple request returned HTTP $status")
            }
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }
