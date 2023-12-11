package com.cellwatch.domain.msak.throughput

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MemorylessTickerTest {
    @Test
    fun testTicker() {
        val ticker = MemorylessTicker(10, 15, 5)
        val times = ArrayList<Instant>()

        ticker.start { times.add(Clock.System.now()) }
        runBlocking { delay(100) }
        ticker.stop()

        runBlocking { delay(100) }

        // it should be impossible to get fewer than 6 or more than 20
        assertEquals(true, times.size >= 6)
        assertEquals(true, times.size <= 20)

        var diffs = ArrayList<Long>()
        for (i in 1 until times.size) diffs.add((times[i] - times[i - 1]).inWholeMilliseconds)

        diffs.forEach {
            // more than min
            assertEquals(true, it >= 5)
            // less than max, with a little buffer for OS delay
            assertEquals(true, it <= 20)
            // not repeated too many times
            assertEquals(true, diffs.count { d -> it == d} <= diffs.size / 2)
        }
    }
}