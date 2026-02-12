package edu.gatech.cc.cellwatch.domain.capability

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class IosPlatformCapabilityProviderTest {

    @Test
    fun captureSnapshot_returnsBestEffortIosSnapshot() {
        runBlocking {
            val snapshot = IosPlatformCapabilityProvider().captureSnapshot()

            assertEquals(CapabilitySupport.NOT_SUPPORTED, snapshot.telephony.support)
            assertEquals(CapabilitySupport.PARTIAL, snapshot.network.support)
            assertEquals("Apple", snapshot.device.manufacturer)
            assertNotNull(snapshot.device.osName)
            assertNotNull(snapshot.device.osVersion)
        }
    }
}
