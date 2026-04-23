package edu.gatech.cc.cellwatch.domain.capability

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class IosPlatformCapabilityProviderTest {

    @Test
    fun captureSnapshot_returnsBestEffortIosSnapshot() {
        runBlocking {
            val snapshot = IosPlatformCapabilityProvider().captureSnapshot()

            assertTrue(
                snapshot.telephony.support == CapabilitySupport.NOT_SUPPORTED ||
                    snapshot.telephony.support == CapabilitySupport.PARTIAL
            )
            assertEquals(CapabilitySupport.PARTIAL, snapshot.network.support)
            assertTrue(
                snapshot.location.support == CapabilitySupport.PERMISSION_DENIED ||
                    snapshot.location.support == CapabilitySupport.PARTIAL ||
                    snapshot.location.support == CapabilitySupport.UNAVAILABLE
            )
            assertEquals("Apple", snapshot.device.manufacturer)
            assertNotNull(snapshot.device.osName)
            assertNotNull(snapshot.device.osVersion)
        }
    }
}
