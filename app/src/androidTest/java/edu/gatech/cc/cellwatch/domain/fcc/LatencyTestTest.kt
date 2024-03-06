package edu.gatech.cc.cellwatch.domain.fcc

import androidx.test.ext.junit.runners.AndroidJUnit4
import edu.gatech.cc.cellwatch.domain.msak.LATENCY_AUTHORIZE_PATH
import edu.gatech.cc.cellwatch.domain.msak.LATENCY_RESULT_PATH
import edu.gatech.cc.cellwatch.domain.msak.Server
import edu.gatech.cc.cellwatch.domain.msak.latency.LatencyMessage
import edu.gatech.cc.cellwatch.domain.msak.latency.LatencyResult
import edu.gatech.cc.cellwatch.domain.msak.latency.LatencyRoundTrip
import edu.gatech.cc.cellwatch.domain.msak.latency.LatencyUpdate
import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.runs
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LatencyTestTest {
    lateinit var test: LatencyTest

    @Before
    fun setup() {
        val server = Server("test", null, mapOf(
            "http:///$LATENCY_AUTHORIZE_PATH" to "http://0.0.0.0/$LATENCY_AUTHORIZE_PATH",
            "http:///$LATENCY_RESULT_PATH" to "http://0.0.0.0/$LATENCY_RESULT_PATH",
        ))

        test = LatencyTest(server, groupId = "")
        mockkObject(test.msakTest)
        every { test.msakTest.start() } just runs
    }
    @Test
    fun testCalculations() {
        every { test.msakTest.start() } answers {
            (self as edu.gatech.cc.cellwatch.domain.msak.latency.LatencyTest).result = LatencyResult(
                "test",
                "",
                listOf(
                    LatencyRoundTrip(5, false),
                    LatencyRoundTrip(7, false),
                    LatencyRoundTrip(3, false),
                    LatencyRoundTrip(6, false),
                    LatencyRoundTrip(2, false),
                    LatencyRoundTrip(9, false),
                    LatencyRoundTrip(6, false),
                    LatencyRoundTrip(4, false),
                    LatencyRoundTrip(0, true),
                    LatencyRoundTrip(12, false),
                ),
                10,
                9,
            )
        }

        (test.msakTest.updatesChan as Channel<LatencyUpdate>).close()
        val result = runBlocking { test.run() }
        assertEquals(6, result.latencyData?.rtt)
        assertEquals(8, result.latencyData?.jitter)
    }

    @Test
    fun testUpdates() {
        runBlocking {
            launch { test.run() }

            (test.msakTest.updatesChan as Channel<LatencyUpdate>).send(LatencyUpdate(
                Clock.System.now(),
                LatencyMessage("s2c", "test", 1, 5),
            ))
            (test.msakTest.updatesChan as Channel<LatencyUpdate>).send(LatencyUpdate(
                Clock.System.now(),
                LatencyMessage("s2c", "test", 0, null),
            ))
            (test.msakTest.updatesChan as Channel<LatencyUpdate>).send(LatencyUpdate(
                Clock.System.now(),
                LatencyMessage("s2c", "test", 2, 7),
            ))

            (test.msakTest.updatesChan as Channel<LatencyUpdate>).close()
        }

        val rtt1 = runBlocking { test.rttChan.receive() }
        assertEquals(5, rtt1)

        val rtt2 = runBlocking { test.rttChan.receive() }
        assertEquals(7, rtt2)
    }
}