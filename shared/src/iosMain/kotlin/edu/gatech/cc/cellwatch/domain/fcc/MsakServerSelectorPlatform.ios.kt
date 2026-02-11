package edu.gatech.cc.cellwatch.domain.fcc

import edu.gatech.cc.cellwatch.msak.shared.Server
import edu.gatech.cc.cellwatch.msak.shared.locate.LocateException
import edu.gatech.cc.cellwatch.msak.shared.locate.LocateManager

actual object MsakServerSelectorPlatform {
    actual fun createLocator(config: MsakLocateConfig): MsakServerLocator {
        val locateManager = LocateManager(
            serverEnv = config.environment.toLocateServerEnv(),
            userAgent = config.userAgent,
            msakLocalServerHost = config.localServerHost,
            msakLocalServerSecure = config.localServerSecure,
        )
        val fallbackHost = config.localServerHost ?: "0.0.0.0"
        return object : MsakServerLocator {
            override val fallbackHost: String = fallbackHost

            override suspend fun locateThroughputServers(): List<MsakServerEndpoint> {
                return try {
                    locateManager.locateThroughputServers().map { it.toEndpoint() }
                } catch (e: LocateException) {
                    throw MsakLocateNetworkUnavailableException("throughput locate failed", e)
                }
            }

            override suspend fun locateLatencyServers(throughputServer: MsakServerEndpoint): List<MsakServerEndpoint> {
                val mapped = Server(
                    machine = throughputServer.machine,
                    location = null,
                    urls = throughputServer.urls,
                )
                return try {
                    locateManager.locateLatencyServers(mapped).map { it.toEndpoint() }
                } catch (e: LocateException) {
                    throw MsakLocateNetworkUnavailableException("latency locate failed", e)
                }
            }
        }
    }

    actual fun createPinger(): MsakHostPinger {
        // iOS pinger intentionally no-op for now; selector falls back to list order from Locate.
        return object : MsakHostPinger {
            override suspend fun pingMs(host: String): Double = 0.0
        }
    }
}

private fun MsakLocateEnvironment.toLocateServerEnv(): LocateManager.ServerEnv {
    return when (this) {
        MsakLocateEnvironment.PROD -> LocateManager.ServerEnv.PROD
        MsakLocateEnvironment.STAGING -> LocateManager.ServerEnv.STAGING
        MsakLocateEnvironment.LOCAL -> LocateManager.ServerEnv.LOCAL
    }
}

private fun Server.toEndpoint(): MsakServerEndpoint {
    return MsakServerEndpoint(
        machine = machine,
        urls = urls,
    )
}
