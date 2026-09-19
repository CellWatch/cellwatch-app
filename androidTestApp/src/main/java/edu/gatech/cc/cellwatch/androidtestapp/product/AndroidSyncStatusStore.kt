package edu.gatech.cc.cellwatch.androidtestapp.product

import android.content.Context
import edu.gatech.cc.cellwatch.domain.sync.SyncStatusRecord
import edu.gatech.cc.cellwatch.domain.sync.SyncStatusStore
import kotlinx.datetime.Instant

/** Android [SyncStatusStore]. Mirrors `AndroidOnboardingProfileStore`. */
class AndroidSyncStatusStore(context: Context) : SyncStatusStore {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun load(): SyncStatusRecord = SyncStatusRecord(
        lastAttemptAt = prefs.getLong(KEY_ATTEMPT, 0L).asInstant(),
        lastSuccessAt = prefs.getLong(KEY_SUCCESS, 0L).asInstant(),
        lastUploadedCount = prefs.getInt(KEY_LAST_COUNT, 0),
        totalUploadedCount = prefs.getInt(KEY_TOTAL_COUNT, 0),
        lastAttemptFailed = prefs.getBoolean(KEY_FAILED, false),
    )

    override fun save(record: SyncStatusRecord) {
        prefs.edit()
            .putLong(KEY_ATTEMPT, record.lastAttemptAt?.toEpochMilliseconds() ?: 0L)
            .putLong(KEY_SUCCESS, record.lastSuccessAt?.toEpochMilliseconds() ?: 0L)
            .putInt(KEY_LAST_COUNT, record.lastUploadedCount)
            .putInt(KEY_TOTAL_COUNT, record.totalUploadedCount)
            .putBoolean(KEY_FAILED, record.lastAttemptFailed)
            .apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }

    // 0 means absent: there is no legitimate epoch-0 sync.
    private fun Long.asInstant(): Instant? =
        takeIf { it > 0L }?.let { Instant.fromEpochMilliseconds(it) }

    private companion object {
        const val PREFS_NAME = "cellwatch_sync_status"
        const val KEY_ATTEMPT = "last_attempt_at"
        const val KEY_SUCCESS = "last_success_at"
        const val KEY_LAST_COUNT = "last_uploaded_count"
        const val KEY_TOTAL_COUNT = "total_uploaded_count"
        const val KEY_FAILED = "last_attempt_failed"
    }
}
