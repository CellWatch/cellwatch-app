package edu.gatech.cc.cellwatch.domain.capability

import edu.gatech.cc.cellwatch.domain.model.NetworkConnectionType
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
            // Network capability is now really detected via nw_path rather than
            // stubbed as PARTIAL. On a simulator the path is satisfied over the
            // host's interface, so expect AVAILABLE - or UNAVAILABLE if the path
            // could not be read in time.
            assertTrue(
                snapshot.network.support == CapabilitySupport.AVAILABLE ||
                    snapshot.network.support == CapabilitySupport.UNAVAILABLE,
                "unexpected network support: ${snapshot.network.support}",
            )
            if (snapshot.network.support == CapabilitySupport.AVAILABLE) {
                assertNotNull(snapshot.network.connectionType)
                // A simulator routes via the host, so it must never claim cellular.
                assertTrue(
                    snapshot.network.connectionType != NetworkConnectionType.CELLULAR,
                    "simulator reported CELLULAR: ${snapshot.network.connectionType}",
                )
            }
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
