package edu.gatech.cc.cellwatch.domain.fcc

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

data class MsakServerSelectionResult(
    val throughputMachine: String,
    val latencyMachine: String,
    val fallbackUsed: Boolean,
)

class MsakServerSelectionHarness(
    private val config: MsakLocateConfig = MsakLocateConfig(
        environment = MsakLocateEnvironment.PROD,
        userAgent = "cellwatch-kmp-harness",
    ),
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun runDefaultScenario(
        onComplete: (MsakServerSelectionResult?, Throwable?) -> Unit,
    ) {
        scope.launch {
            runCatching {
                val locator = MsakServerSelectorPlatform.createLocator(config)
                val pinger = MsakServerSelectorPlatform.createPinger()
                val pair = MsakServerSelector.chooseServers(locator, pinger)
                val fallbackUsed = pair.throughputServer.machine == locator.fallbackHost &&
                    pair.latencyServer.machine == locator.fallbackHost
                MsakServerSelectionResult(
                    throughputMachine = pair.throughputServer.machine,
                    latencyMachine = pair.latencyServer.machine,
                    fallbackUsed = fallbackUsed,
                )
            }.onSuccess {
                onComplete(it, null)
            }.onFailure {
                onComplete(null, it)
            }
        }
    }

    fun close() {
        scope.cancel()
    }
}
