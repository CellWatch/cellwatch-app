package edu.gatech.cc.cellwatch.domain.fcc

enum class MsakLocateEnvironment {
    PROD,
    STAGING,
    LOCAL,
}

data class MsakLocateConfig(
    val environment: MsakLocateEnvironment,
    val userAgent: String? = null,
    val localServerHost: String? = null,
    val localServerSecure: Boolean = false,
)

expect object MsakServerSelectorPlatform {
    fun createLocator(config: MsakLocateConfig): MsakServerLocator
    fun createPinger(): MsakHostPinger
}
