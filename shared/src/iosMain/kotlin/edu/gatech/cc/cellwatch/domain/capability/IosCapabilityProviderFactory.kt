package edu.gatech.cc.cellwatch.domain.capability

import kotlinx.datetime.Clock

fun createIosPlatformCapabilityProvider(): PlatformCapabilityProvider {
    return IosPlatformCapabilityProvider(clock = Clock.System)
}
