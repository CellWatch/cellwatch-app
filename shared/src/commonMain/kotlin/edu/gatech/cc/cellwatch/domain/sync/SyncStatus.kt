package edu.gatech.cc.cellwatch.domain.sync

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * What the last upload attempt did, kept across launches.
 *
 * Success and failure are both recorded because "nothing has been uploaded
 * lately" and "uploads are failing" look identical from the pending queue
 * alone, and only the second one asks anything of the user.
 */
data class SyncStatusRecord(
    val lastAttemptAt: Instant? = null,
    val lastSuccessAt: Instant? = null,
    /** Records uploaded by the last attempt that uploaded anything. */
    val lastUploadedCount: Int = 0,
    /** Cumulative, so the user sees total contribution rather than one run. */
    val totalUploadedCount: Int = 0,
    val lastAttemptFailed: Boolean = false,
)

interface SyncStatusStore {
    fun load(): SyncStatusRecord
    fun save(record: SyncStatusRecord)
    fun clear()
}

class InMemorySyncStatusStore(
    private var record: SyncStatusRecord = SyncStatusRecord(),
) : SyncStatusStore {
    override fun load(): SyncStatusRecord = record
    override fun save(record: SyncStatusRecord) {
        this.record = record
    }
    override fun clear() {
        record = SyncStatusRecord()
    }
}

enum class SyncStatusTone { NEUTRAL, SUCCESS, WARNING }

data class SyncStatusSummary(
    val headline: String,
    /** Null when there is nothing further worth saying. */
    val detail: String?,
    val tone: SyncStatusTone,
)

/**
 * Turns the sync record and the pending queue into something a person can read.
 *
 * Replaces "Sync status unknown. Open History to refresh." - which was shown
 * whenever counts had not been loaded, said nothing, and pointed at a screen
 * that does not exist yet.
 *
 * The FCC sentence is deliberately repeated on every non-empty state: uploading
 * to the CellWatch server is not the same event as a challenge submission
 * reaching the FCC, and a user who conflates the two will think a measurement
 * has been filed when it has not.
 */
object SyncStatusPresenter {

    const val FCC_NOTE = "Submission to the FCC happens separately, later."

    fun present(
        record: SyncStatusRecord,
        pendingRecords: Int,
        inProgress: Boolean,
        now: Instant,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): SyncStatusSummary {
        if (inProgress) {
            return SyncStatusSummary(
                headline = "Uploading to the CellWatch server…",
                detail = FCC_NOTE,
                tone = SyncStatusTone.NEUTRAL,
            )
        }

        val neverAttempted = record.lastAttemptAt == null
        if (neverAttempted && pendingRecords == 0) {
            return SyncStatusSummary(
                headline = "Nothing to upload yet.",
                detail = "Take a measurement to get started.",
                tone = SyncStatusTone.NEUTRAL,
            )
        }

        if (record.lastAttemptFailed) {
            val failedAt = record.lastAttemptAt?.let { describe(it, now, timeZone) }
            return SyncStatusSummary(
                headline = "Last upload attempt failed${failedAt?.let { " $it" }.orEmpty()}.",
                detail = buildString {
                    append(pendingPhrase(pendingRecords))
                    append(" They are saved on this device and will be retried. ")
                    append(lastSuccessPhrase(record, now, timeZone))
                    append(FCC_NOTE)
                },
                tone = SyncStatusTone.WARNING,
            )
        }

        val successAt = record.lastSuccessAt
        if (successAt == null) {
            return SyncStatusSummary(
                headline = "Not uploaded yet.",
                detail = "${pendingPhrase(pendingRecords)} $FCC_NOTE",
                tone = SyncStatusTone.NEUTRAL,
            )
        }

        return SyncStatusSummary(
            headline = "Last uploaded ${describe(successAt, now, timeZone)}.",
            detail = buildString {
                append(
                    "${record.totalUploadedCount} record${plural(record.totalUploadedCount)} " +
                        "uploaded to the CellWatch server. ",
                )
                if (pendingRecords > 0) {
                    append("${pendingPhrase(pendingRecords)} ")
                }
                append(FCC_NOTE)
            },
            tone = if (pendingRecords > 0) SyncStatusTone.NEUTRAL else SyncStatusTone.SUCCESS,
        )
    }

    private fun pendingPhrase(pendingRecords: Int): String = when (pendingRecords) {
        0 -> "Nothing is waiting to upload."
        1 -> "1 record is waiting to upload."
        else -> "$pendingRecords records are waiting to upload."
    }

    private fun lastSuccessPhrase(record: SyncStatusRecord, now: Instant, timeZone: TimeZone): String {
        val at = record.lastSuccessAt ?: return "Nothing has uploaded successfully yet. "
        return "Last successful upload was ${describe(at, now, timeZone)}. "
    }

    private fun plural(count: Int): String = if (count == 1) "" else "s"

    /**
     * Relative for anything recent, absolute beyond that.
     *
     * A bare timestamp for something a minute old reads as stale, and a
     * relative one for something four days old is useless for deciding whether
     * to worry.
     */
    private fun describe(at: Instant, now: Instant, timeZone: TimeZone): String {
        val seconds = (now - at).inWholeSeconds
        return when {
            seconds < 0 -> "just now"
            seconds < 60 -> "just now"
            seconds < 3_600 -> "${seconds / 60} minute${plural((seconds / 60).toInt())} ago"
            seconds < 86_400 -> "${seconds / 3_600} hour${plural((seconds / 3_600).toInt())} ago"
            else -> "on ${formatDate(at, timeZone)}"
        }
    }

    private fun formatDate(at: Instant, timeZone: TimeZone): String {
        val local = at.toLocalDateTime(timeZone)
        val month = MONTHS[local.monthNumber - 1]
        val hour = local.hour.toString().padStart(2, '0')
        val minute = local.minute.toString().padStart(2, '0')
        return "${local.dayOfMonth} $month ${local.year} at $hour:$minute"
    }

    private val MONTHS = listOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
    )
}
