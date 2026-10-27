package edu.gatech.cc.cellwatch.data.core.sync

import androidx.work.NetworkType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class UploadWorkerTest {
    @Test
    fun periodicUploadRunsHourlyOnceThePhoneHasAConnection() {
        val spec = UploadWorker.periodicRequest().workSpec

        assertEquals(UploadWorker::class.java.name, spec.workerClassName)
        assertTrue(spec.isPeriodic)
        assertEquals(TimeUnit.HOURS.toMillis(1), spec.intervalDuration)
        assertEquals(NetworkType.CONNECTED, spec.constraints.requiredNetworkType)
    }
}
