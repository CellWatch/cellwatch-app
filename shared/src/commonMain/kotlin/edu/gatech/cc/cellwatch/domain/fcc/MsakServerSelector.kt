package edu.gatech.cc.cellwatch.domain.fcc

private const val THROUGHPUT_DOWNLOAD_PATH = "throughput/v1/download"
private const val THROUGHPUT_UPLOAD_PATH = "throughput/v1/upload"
private const val LATENCY_AUTHORIZE_PATH = "latency/v1/authorize"
private const val LATENCY_RESULT_PATH = "latency/v1/result"

data class MsakServerEndpoint(
    val machine: String,
    val urls: Map<String, String> = emptyMap(),
)

interface MsakHostPinger {
    suspend fun pingMs(host: String): Double
}

interface MsakServerLocator {
    val fallbackHost: String
    suspend fun locateThroughputServers(): List<MsakServerEndpoint>
    suspend fun locateLatencyServers(throughputServer: MsakServerEndpoint): List<MsakServerEndpoint>
}

class MsakLocateNetworkUnavailableException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

data class MsakServerPair(
    val throughputServer: MsakServerEndpoint,
    val latencyServer: MsakServerEndpoint,
)

object MsakServerSelector {
    suspend fun chooseServers(
        locator: MsakServerLocator,
        pinger: MsakHostPinger,
    ): MsakServerPair {
        return try {
            val throughputServers = locator.locateThroughputServers()
            check(throughputServers.isNotEmpty()) { "no throughput servers found" }

            val throughputServer = try {
                throughputServers.maxBy { server ->
                    pinger.pingMs(server.machine)
                }
            } catch (_: Throwable) {
                throughputServers.first()
            }

            val latencyServers = locator.locateLatencyServers(throughputServer)
            check(latencyServers.isNotEmpty()) { "no latency servers found" }

            val latencyServer = latencyServers.firstOrNull { it.machine == throughputServer.machine }
                ?: latencyServers.first()

            MsakServerPair(
                throughputServer = throughputServer,
                latencyServer = latencyServer,
            )
        } catch (e: MsakLocateNetworkUnavailableException) {
            val fallback = createUnreachableServer(locator.fallbackHost)
            MsakServerPair(
                throughputServer = fallback,
                latencyServer = fallback,
            )
        }
    }

    fun createUnreachableServer(host: String): MsakServerEndpoint {
        return MsakServerEndpoint(
            machine = host,
            urls = mapOf(
                "ws:///$THROUGHPUT_UPLOAD_PATH" to "ws://0.0.0.0",
                "ws:///$THROUGHPUT_DOWNLOAD_PATH" to "ws://0.0.0.0",
                "http:///$LATENCY_AUTHORIZE_PATH" to "ws://0.0.0.0",
                "http:///$LATENCY_RESULT_PATH" to "ws://0.0.0.0",
            ),
        )
    }
}
