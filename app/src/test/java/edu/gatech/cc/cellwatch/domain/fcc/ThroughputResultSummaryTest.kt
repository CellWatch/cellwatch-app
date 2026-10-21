package edu.gatech.cc.cellwatch.domain.fcc

import edu.gatech.cc.cellwatch.msak.throughput.ByteCounters
import edu.gatech.cc.cellwatch.msak.throughput.ThroughputMeasurement
import edu.gatech.cc.cellwatch.msak.throughput.ThroughputStream
import edu.gatech.cc.cellwatch.msak.throughput.ThroughputUpdate
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

/** Tests how [ThroughputTest] turns a finished msak throughput test into a [ThroughputResult]. */
class ThroughputResultSummaryTest {
    private val start = Instant.fromEpochMilliseconds(1_790_000_000_000)
    private val activeStart = start + 2.seconds
    private val end = activeStart + 10.seconds
    private val now = end + 1.seconds

    // Each of 3 streams receives 100 bytes in the 1 s before the warmup ends, then 1000 bytes in
    // the 1 s after: 1000 B/s per stream in the active period.
    private val warmup = List(3) { update(it, bytes = 100, elapsedUsecs = 1_000_000) }
    private val active = List(3) { update(it, bytes = 1_100, elapsedUsecs = 2_000_000) }

    @Test
    fun completedTestIsSuccessful() {
        val r = summarize(latest = active, lastWarmupUpdates = warmup)

        assertEquals(true, r.success)
        assertEquals("throughput.example", r.targetHost)
        assertEquals(start, r.start)
        assertEquals(ThroughputMetrics(300, 2_000_000, 300.0), r.warmupMetrics)
        assertEquals(ThroughputMetrics(3_000, 10_000_000, 3_000.0), r.activeMetrics)
    }

    @Test
    fun streamsThatEndedWithNetworkErrorsAfterTransferringDataAreSuccessful() {
        // The FCC counts a test that ended early because of a network error as successful if it
        // transferred >0 bytes.
        val r = summarize(
            latest = active,
            lastWarmupUpdates = warmup,
            streamErrors = listOf(
                ThroughputStream.UnexpectedCloseException(1006, "closed"),
                ThroughputStream.FailureException(),
                ThroughputStream.UploadDataException(),
            ),
        )

        assertEquals(true, r.success)
        assertEquals(3_000L, r.activeMetrics.bytes)
    }

    @Test
    fun otherStreamErrorsAreThrownBecauseTheyInvalidateTheTest() {
        for (error in listOf(ThroughputStream.NotStartedException(), IllegalStateException("bug"))) {
            val thrown = assertThrows(Throwable::class.java) {
                summarize(latest = active, lastWarmupUpdates = warmup, streamErrors = listOf(null, error, null))
            }
            assertSame(error, thrown)
        }
    }

    @Test
    fun testErrorOtherThanMissingWarmupUpdatesIsThrown() {
        val error = IllegalStateException("timer callback failed")

        val thrown = assertThrows(Throwable::class.java) {
            summarize(latest = active, lastWarmupUpdates = warmup, error = error)
        }

        assertSame(error, thrown)
    }

    @Test
    fun streamWithoutWarmupUpdatesFailsTheTest() {
        // startActive() stops the test and leaves lastWarmupUpdates null when a stream had no
        // updates when the warmup ended; data may still arrive before the streams close.
        val r = summarize(
            latest = active,
            lastWarmupUpdates = null,
            error = ThroughputTest.MissingWarmupUpdateException(),
        )

        assertEquals(false, r.success)
    }

    @Test
    fun missingWarmupUpdatesFailTheTestWithoutCheckingStreamErrors() {
        val r = summarize(
            latest = active,
            lastWarmupUpdates = null,
            error = ThroughputTest.MissingWarmupUpdateException(),
            streamErrors = listOf(IllegalStateException("not thrown"), null, null),
        )

        assertEquals(false, r.success)
    }

    @Test
    fun testThatEndedDuringWarmupFails() {
        val r = summarize(activeStart = null, latest = warmup, lastWarmupUpdates = null)

        assertEquals(false, r.success)
        assertEquals("warmup runs until the summary", ThroughputMetrics(300, 13_000_000, 300.0), r.warmupMetrics)
        assertEquals(ThroughputMetrics(0, 0, 0.0), r.activeMetrics)
    }

    @Test
    fun noBytesReceivedDuringTheActivePeriodFails() {
        val stalled = List(3) { update(it, bytes = 100, elapsedUsecs = 2_000_000) }

        val r = summarize(latest = stalled, lastWarmupUpdates = warmup)

        assertEquals(false, r.success)
        assertEquals(0L, r.activeMetrics.bytes)
        assertEquals(0.0, r.activeMetrics.bytesPerSec, 0.0)
    }

    @Test
    fun streamWithNoUpdateDuringTheActivePeriodFailsTheTest() {
        // Without an active update for every stream the active metrics can't be computed, so they
        // are zeroed and the test fails even though the other streams transferred data.
        val r = summarize(latest = listOf(active[0], active[1], warmup[2]), lastWarmupUpdates = warmup)

        assertEquals(false, r.success)
        assertEquals(ThroughputMetrics(0, 0, 0.0), r.activeMetrics)
        assertEquals(ThroughputMetrics(300, 2_000_000, 300.0), r.warmupMetrics)
    }

    @Test
    fun streamThatNeverReportedFailsTheTest() {
        val r = summarize(activeStart = null, latest = listOf(warmup[0], null, warmup[2]), lastWarmupUpdates = null)

        assertEquals(false, r.success)
        assertEquals(ThroughputMetrics(200, 13_000_000, 200.0), r.warmupMetrics)
    }

    @Test
    fun testWithNoUpdatesAtAllFails() {
        val r = summarize(activeStart = null, latest = listOf(null, null, null), lastWarmupUpdates = null)

        assertEquals(false, r.success)
        assertEquals(ThroughputMetrics(0, 13_000_000, 0.0), r.warmupMetrics)
        assertEquals(ThroughputMetrics(0, 0, 0.0), r.activeMetrics)
    }

    @Test
    fun activePeriodRunsUntilNowIfTheTestHasNoEndTime() {
        val r = summarize(end = null, latest = active, lastWarmupUpdates = warmup)

        assertEquals(true, r.success)
        assertEquals(11_000_000L, r.activeMetrics.usecs)
    }

    @Test
    fun networkByteCountsArePreferredOverApplicationByteCounts() {
        val appOnly = { stream: Int, bytes: Long, elapsed: Long ->
            ThroughputUpdate(false, stream, start, ThroughputMeasurement(null, ByteCounters(0, bytes), elapsed))
        }

        val withNetwork = summarize(
            latest = List(3) { update(it, bytes = 1_100, elapsedUsecs = 2_000_000, appBytes = 1) },
            lastWarmupUpdates = List(3) { update(it, bytes = 100, elapsedUsecs = 1_000_000, appBytes = 1) },
        )
        val withoutNetwork = summarize(
            latest = List(3) { appOnly(it, 600, 2_000_000) },
            lastWarmupUpdates = List(3) { appOnly(it, 100, 1_000_000) },
        )

        assertEquals(3_000L, withNetwork.activeMetrics.bytes)
        assertEquals(1_500L, withoutNetwork.activeMetrics.bytes)
        assertEquals(true, withoutNetwork.success)
    }

    @Test
    fun unreachableServerIsAFailedResultWithNoData() {
        val r = ThroughputTest.unreachableResult("locate.example", start)

        assertEquals(false, r.success)
        assertEquals("locate.example", r.targetHost)
        assertEquals(ThroughputMetrics(0, 0, 0.0), r.warmupMetrics)
        assertEquals(ThroughputMetrics(0, 0, 0.0), r.activeMetrics)
    }

    private fun update(stream: Int, bytes: Long, elapsedUsecs: Long, appBytes: Long = bytes) = ThroughputUpdate(
        false,
        stream,
        start,
        ThroughputMeasurement(ByteCounters(0, bytes), ByteCounters(0, appBytes), elapsedUsecs),
    )

    private fun summarize(
        activeStart: Instant? = this.activeStart,
        end: Instant? = this.end,
        latest: List<ThroughputUpdate?>,
        lastWarmupUpdates: List<ThroughputUpdate>?,
        error: Throwable? = null,
        streamErrors: List<Throwable?> = listOf(null, null, null),
    ) = ThroughputTest.summarize(
        serverHost = "throughput.example",
        start = start,
        activeStart = activeStart,
        end = end,
        latest = latest,
        lastWarmupUpdates = lastWarmupUpdates,
        error = error,
        streamErrors = streamErrors,
        now = { now },
    )
}
