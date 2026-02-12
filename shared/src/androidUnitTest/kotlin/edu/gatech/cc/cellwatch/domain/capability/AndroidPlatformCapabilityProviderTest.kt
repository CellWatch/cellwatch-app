package edu.gatech.cc.cellwatch.domain.capability

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AndroidPlatformCapabilityProviderTest {

    @Test
    fun captureSnapshot_returnsBestEffortSnapshotWithoutCrashing() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val snapshot = AndroidPlatformCapabilityProvider(context).captureSnapshot()

        assertNotNull(snapshot.capturedAt)
        assertTrue(
            snapshot.telephony.support == CapabilitySupport.PERMISSION_DENIED ||
                snapshot.telephony.support == CapabilitySupport.PARTIAL ||
                snapshot.telephony.support == CapabilitySupport.UNAVAILABLE
        )
        assertNotNull(snapshot.network.support)
        assertTrue(snapshot.device.osName == "Android")
        assertNotNull(snapshot.device.model)
    }
}
