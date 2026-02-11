package edu.gatech.cc.cellwatch.domain.fcc

actual object MsakServerSelectorPlatform {
    actual fun createLocator(config: MsakLocateConfig): MsakServerLocator {
        throw UnsupportedOperationException(
            "JVM host locator adapter is not wired for this module; use Android/iOS platform adapters."
        )
    }

    actual fun createPinger(): MsakHostPinger {
        return object : MsakHostPinger {
            override suspend fun pingMs(host: String): Double = 0.0
        }
    }
}
