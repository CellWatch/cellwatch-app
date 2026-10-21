package edu.gatech.cc.cellwatch.domain.fcc

import edu.gatech.cc.cellwatch.msak.latency.LatencyRoundTrip
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import edu.gatech.cc.cellwatch.msak.latency.LatencyResult as MsakLatencyResult
import edu.gatech.cc.cellwatch.msak.latency.LatencyTest as MsakLatencyTest

/** Tests how [LatencyTest] turns a finished msak latency test into a [LatencyResult]. */
class LatencyResultSummaryTest {
    private val start = Instant.fromEpochMilliseconds(1_790_000_000_000)
    private val end = Instant.fromEpochMilliseconds(1_790_000_005_000)

    // RTTs in microseconds. The lost packet's RTT must not count towards the mean or jitter.
    private val roundTrips = listOf(5, 7, 3, 6, 2, 9, 6, 4, 12).map { LatencyRoundTrip(it * 1000, false) } +
        LatencyRoundTrip(0, true)

    @Test
    fun receivedPacketsMakeASuccessfulResult() {
        val r = summarize(null, msakResult(roundTrips, sent = 10, received = 9))

        assertEquals(true, r.success)
        assertEquals("latency.example", r.targetHost)
        assertEquals(start, r.start)
        assertEquals(5_000_000L, r.usecs)
        assertEquals(6_000, r.meanRtt)
        assertEquals("population std dev of the received RTTs", 2_905, r.jitter)
        assertEquals(10, r.packetsSent)
        assertEquals(9, r.packetsReceived)
    }

    @Test
    fun singleReceivedPacketIsEnoughForSuccess() {
        val r = summarize(null, msakResult(listOf(LatencyRoundTrip(20_000, false)), sent = 10, received = 1))

        assertEquals(true, r.success)
        assertEquals(20_000, r.meanRtt)
        assertEquals(0, r.jitter)
    }

    @Test
    fun noReceivedPacketsIsAFailedResult() {
        val allLost = List(10) { LatencyRoundTrip(0, true) }

        val r = summarize(null, msakResult(allLost, sent = 10, received = 0))

        assertEquals(false, r.success)
        assertEquals("no NaN from averaging zero RTTs", 0, r.meanRtt)
        assertEquals(0, r.jitter)
        assertEquals(10, r.packetsSent)
        assertEquals(0, r.packetsReceived)
    }

    @Test
    fun missingResultIsAFailedResult() {
        val r = summarize(null, null)

        assertEquals(false, r.success)
        assertEquals(0, r.meanRtt)
        assertEquals(0, r.jitter)
        assertEquals(0, r.packetsSent)
        assertEquals(0, r.packetsReceived)
        assertEquals(5_000_000L, r.usecs)
    }

    @Test
    fun networkErrorsWithoutAResultAreFailedResults() {
        for (error in networkErrors()) {
            val r = summarize(error, null)
            assertEquals("${error::class.simpleName}", false, r.success)
        }
    }

    @Test
    fun networkErrorsAfterPacketsWereReceivedKeepTheResultSuccessful() {
        // The FCC counts a test that ended early because of a network error as successful if it
        // received >0 packets.
        for (error in networkErrors()) {
            val r = summarize(error, msakResult(roundTrips, sent = 10, received = 9))
            assertEquals("${error::class.simpleName}", true, r.success)
            assertEquals("${error::class.simpleName}", 9, r.packetsReceived)
        }
    }

    @Test
    fun otherErrorsAreThrownBecauseTheyInvalidateTheTest() {
        val result = msakResult(roundTrips, sent = 10, received = 9)
        val errors = listOf(
            MsakLatencyTest.UnauthorizedException(),
            MsakLatencyTest.NoResultException(),
            IllegalStateException("bug"),
        )

        for (error in errors) {
            val thrown = assertThrows(Throwable::class.java) { summarize(error, result) }
            assertSame(error, thrown)
        }
    }

    @Test
    fun unreachableServerIsAFailedResultWithNoData() {
        val r = LatencyTest.unreachableResult("locate.example", start)

        assertEquals(LatencyResult("locate.example", false, start, 0, 0, 0, 0, 0), r)
    }

    private fun networkErrors() = listOf(
        MsakLatencyTest.AuthorizeFailureExecption(),
        MsakLatencyTest.ResultFailureException(),
        MsakLatencyTest.NoAddrException(),
        MsakLatencyTest.InitialPacketTimeoutException(),
    )

    private fun msakResult(roundTrips: List<LatencyRoundTrip>, sent: Int, received: Int) =
        MsakLatencyResult("id", start.toString(), roundTrips, sent, received)

    private fun summarize(error: Throwable?, result: MsakLatencyResult?) =
        LatencyTest.summarize("latency.example", error, result, start, end)
}
