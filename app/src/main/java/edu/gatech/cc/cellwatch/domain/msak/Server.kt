package edu.gatech.cc.cellwatch.domain.msak

import edu.gatech.cc.cellwatch.domain.msak.throughput.ThroughputDirection
import io.ktor.http.URLBuilder
import io.ktor.http.Url

open class Server(
    val machine: String,
    val location: ServerLocation?,
    val urls: Map<String, String>,
) {
    fun getThroughputUrl(
        direction: ThroughputDirection,
        streams: Int,
        duration: Long,
        delay: Long,
        measurementId: String?,
    ): String {
        val path = when (direction) {
            ThroughputDirection.UPLOAD -> THROUGHPUT_UPLOAD_PATH
            ThroughputDirection.DOWNLOAD -> THROUGHPUT_DOWNLOAD_PATH
        }

        val url = URLBuilder(Url(urls["wss:///$path"] ?: urls ["ws:///$path"] ?: throw Exception("no URL found")))

        url.parameters["streams"] = "$streams"
        url.parameters["duration"] = "$duration"
        url.parameters["delay"] = "$delay"
        if (measurementId != null) {
            url.parameters["mid"] = measurementId
        }

        return url.buildString()
    }

    fun getLatencyAuthorizeUrl(measurementId: String? = null): String {
        return getLatencyUrl(LATENCY_AUTHORIZE_PATH, measurementId)
    }

    fun getLatencyResultUrl(measurementId: String? = null): String {
        return getLatencyUrl(LATENCY_RESULT_PATH, measurementId)
    }

    private fun getLatencyUrl(
        path: String,
        measurementId: String?,
    ): String {
        val url = URLBuilder(Url(urls["https:///$path"] ?: urls ["http:///$path"] ?: throw Exception("no URL found")))

        if (measurementId != null) {
            url.parameters["mid"] = measurementId
        }

        return url.buildString()
    }

    override fun equals(other: Any?): Boolean {
        if (other !is Server) return false
        return other.machine == machine && other.location == location && other.urls == urls
    }

    override fun toString(): String {
        return "${this::class.simpleName}(machine=$machine, location=$location, urls=$urls)"
    }

    override fun hashCode(): Int {
        var result = machine.hashCode()
        result = 31 * result + (location?.hashCode() ?: 0)
        result = 31 * result + urls.hashCode()
        return result
    }
}
