package edu.gatech.cc.cellwatch.domain.sync

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone

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

    /** Kept on the presenter because callers and tests already reach for it here. */
    val FCC_NOTE: String get() = SyncCopy.FCC_NOTE

    fun present(
        record: SyncStatusRecord,
        pendingRecords: Int,
        inProgress: Boolean,
        now: Instant,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): SyncStatusSummary {
        if (inProgress) {
            return SyncStatusSummary(
                headline = SyncCopy.UPLOADING,
                detail = SyncCopy.FCC_NOTE,
                tone = SyncStatusTone.NEUTRAL,
            )
        }

        val neverAttempted = record.lastAttemptAt == null
        if (neverAttempted && pendingRecords == 0) {
            return SyncStatusSummary(
                headline = SyncCopy.NOTHING_TO_UPLOAD,
                detail = SyncCopy.TAKE_A_MEASUREMENT,
                tone = SyncStatusTone.NEUTRAL,
            )
        }

        if (record.lastAttemptFailed) {
            val failedAt = record.lastAttemptAt?.let { SyncCopy.describe(it, now, timeZone) }
            return SyncStatusSummary(
                headline = SyncCopy.lastAttemptFailed(failedAt),
                detail = buildString {
                    append(SyncCopy.pendingPhrase(pendingRecords))
                    append(SyncCopy.WILL_BE_RETRIED)
                    append(lastSuccessPhrase(record, now, timeZone))
                    append(SyncCopy.FCC_NOTE)
                },
                tone = SyncStatusTone.WARNING,
            )
        }

        val successAt = record.lastSuccessAt
        if (successAt == null) {
            return SyncStatusSummary(
                headline = SyncCopy.NOT_UPLOADED_YET,
                detail = "${SyncCopy.pendingPhrase(pendingRecords)} ${SyncCopy.FCC_NOTE}",
                tone = SyncStatusTone.NEUTRAL,
            )
        }

        return SyncStatusSummary(
            headline = SyncCopy.lastUploaded(SyncCopy.describe(successAt, now, timeZone)),
            detail = buildString {
                append(SyncCopy.totalUploaded(record.totalUploadedCount))
                if (pendingRecords > 0) {
                    append("${SyncCopy.pendingPhrase(pendingRecords)} ")
                }
                append(SyncCopy.FCC_NOTE)
            },
            tone = if (pendingRecords > 0) SyncStatusTone.NEUTRAL else SyncStatusTone.SUCCESS,
        )
    }

    private fun lastSuccessPhrase(record: SyncStatusRecord, now: Instant, timeZone: TimeZone): String {
        val at = record.lastSuccessAt ?: return SyncCopy.NOTHING_UPLOADED_SUCCESSFULLY
        return SyncCopy.lastSuccessfulUpload(SyncCopy.describe(at, now, timeZone))
    }
}
