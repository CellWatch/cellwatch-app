package edu.gatech.cc.cellwatch.domain.sync

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SyncStatusPresenterTest {

    private val now = Instant.parse("2026-09-19T20:00:00Z")
    private val utc = TimeZone.UTC

    private fun present(
        record: SyncStatusRecord = SyncStatusRecord(),
        pending: Int = 0,
        inProgress: Boolean = false,
    ) = SyncStatusPresenter.present(record, pending, inProgress, now, utc)

    @Test
    fun `in progress says so and does not claim a queue`() {
        val summary = present(inProgress = true)

        assertTrue(summary.headline.contains("Uploading"))
        assertEquals(SyncStatusTone.NEUTRAL, summary.tone)
    }

    @Test
    fun `a fresh install is not described as a failure`() {
        val summary = present()

        assertTrue(summary.headline.contains("Nothing to upload"))
        assertEquals(SyncStatusTone.NEUTRAL, summary.tone)
    }

    @Test
    fun `a failed attempt names when it failed and says records are kept`() {
        val summary = present(
            record = SyncStatusRecord(
                lastAttemptAt = now.minus(kotlin.time.Duration.parse("30m")),
                lastSuccessAt = Instant.parse("2026-09-17T09:15:00Z"),
                totalUploadedCount = 9,
                lastAttemptFailed = true,
            ),
            pending = 3,
        )

        assertEquals(SyncStatusTone.WARNING, summary.tone)
        assertTrue(summary.headline.contains("30 minutes ago"), summary.headline)
        val detail = summary.detail.orEmpty()
        assertTrue(detail.contains("3 records are waiting"), detail)
        assertTrue(detail.contains("saved on this device"), detail)
        // The last success still matters: it separates "always broken" from
        // "worked yesterday, failing now".
        assertTrue(detail.contains("17 Sep 2026"), detail)
        assertTrue(detail.contains(SyncStatusPresenter.FCC_NOTE), detail)
    }

    @Test
    fun `a clean success reports the total and stays out of the way`() {
        val summary = present(
            record = SyncStatusRecord(
                lastAttemptAt = now,
                lastSuccessAt = now,
                lastUploadedCount = 3,
                totalUploadedCount = 12,
            ),
        )

        assertEquals(SyncStatusTone.SUCCESS, summary.tone)
        assertTrue(summary.headline.contains("just now"), summary.headline)
        assertTrue(summary.detail.orEmpty().contains("12 records uploaded"), summary.detail.orEmpty())
    }

    @Test
    fun `success with a queue is not reported as fully synced`() {
        val summary = present(
            record = SyncStatusRecord(
                lastAttemptAt = now,
                lastSuccessAt = now,
                totalUploadedCount = 3,
            ),
            pending = 2,
        )

        assertEquals(SyncStatusTone.NEUTRAL, summary.tone)
        assertTrue(summary.detail.orEmpty().contains("2 records are waiting"))
    }

    @Test
    fun `one record is singular`() {
        val summary = present(
            record = SyncStatusRecord(lastAttemptAt = now, lastSuccessAt = now, totalUploadedCount = 1),
            pending = 1,
        )

        assertTrue(summary.detail.orEmpty().contains("1 record uploaded"), summary.detail.orEmpty())
        assertTrue(summary.detail.orEmpty().contains("1 record is waiting"), summary.detail.orEmpty())
    }

    @Test
    fun `every non-empty state mentions that the FCC step is separate`() {
        val states = listOf(
            present(inProgress = true),
            present(record = SyncStatusRecord(lastAttemptAt = now, lastAttemptFailed = true), pending = 1),
            present(record = SyncStatusRecord(lastAttemptAt = now), pending = 1),
            present(record = SyncStatusRecord(lastAttemptAt = now, lastSuccessAt = now, totalUploadedCount = 1)),
        )

        states.forEach { summary ->
            assertTrue(
                summary.detail.orEmpty().contains(SyncStatusPresenter.FCC_NOTE),
                "missing the FCC note: $summary",
            )
        }
    }
}
