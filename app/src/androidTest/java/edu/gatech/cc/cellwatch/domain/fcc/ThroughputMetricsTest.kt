package edu.gatech.cc.cellwatch.domain.fcc

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThroughputMetricsTest {
    @Test
    fun testBytesPerSec() {
        assertEquals(0.0, ThroughputMetrics(0, 0).bytesPerSec, 0.0)
        assertEquals(0.0, ThroughputMetrics(10, 0).bytesPerSec, 0.0)
        assertEquals(0.0, ThroughputMetrics(0, 10).bytesPerSec, 0.0)
        assertEquals(1740000.0, ThroughputMetrics(87, 50).bytesPerSec, 0.001)
        assertEquals(1691.542, ThroughputMetrics(17, 10050).bytesPerSec, 0.001)
    }

    @Test
    fun testMinus() {
        assertEquals(10, (ThroughputMetrics(50, 20) - ThroughputMetrics(40, 15)).bytes)
        assertEquals(5, (ThroughputMetrics(50, 20) - ThroughputMetrics(40, 15)).usecs)
        assertEquals(2000000.0, (ThroughputMetrics(50, 20) - ThroughputMetrics(40, 15)).bytesPerSec, 0.001)
    }
}