package edu.gatech.cc.cellwatch.domain.app

import edu.gatech.cc.cellwatch.domain.sync.SyncStatusRecord
import edu.gatech.cc.cellwatch.domain.sync.SyncStatusStore
import kotlinx.datetime.Instant
import platform.Foundation.NSUserDefaults

/** iOS [SyncStatusStore]. Counterpart of `AndroidSyncStatusStore`. */
class IosSyncStatusStore(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : SyncStatusStore {

    override fun load(): SyncStatusRecord = SyncStatusRecord(
        lastAttemptAt = defaults.doubleForKey(KEY_ATTEMPT).asInstant(),
        lastSuccessAt = defaults.doubleForKey(KEY_SUCCESS).asInstant(),
        lastUploadedCount = defaults.integerForKey(KEY_LAST_COUNT).toInt(),
        totalUploadedCount = defaults.integerForKey(KEY_TOTAL_COUNT).toInt(),
        lastAttemptFailed = defaults.boolForKey(KEY_FAILED),
    )

    override fun save(record: SyncStatusRecord) {
        defaults.setDouble(record.lastAttemptAt.asDouble(), KEY_ATTEMPT)
        defaults.setDouble(record.lastSuccessAt.asDouble(), KEY_SUCCESS)
        defaults.setInteger(record.lastUploadedCount.toLong(), KEY_LAST_COUNT)
        defaults.setInteger(record.totalUploadedCount.toLong(), KEY_TOTAL_COUNT)
        defaults.setBool(record.lastAttemptFailed, KEY_FAILED)
    }

    override fun clear() {
        listOf(KEY_ATTEMPT, KEY_SUCCESS, KEY_LAST_COUNT, KEY_TOTAL_COUNT, KEY_FAILED)
            .forEach(defaults::removeObjectForKey)
    }

    // 0 means absent, matching Android: there is no legitimate epoch-0 sync.
    // NSUserDefaults returns 0.0 for a missing key rather than signalling it.
    private fun Double.asInstant(): Instant? =
        takeIf { it > 0.0 }?.let { Instant.fromEpochMilliseconds(it.toLong()) }

    private fun Instant?.asDouble(): Double = this?.toEpochMilliseconds()?.toDouble() ?: 0.0

    private companion object {
        const val KEY_ATTEMPT = "cellwatch.sync.last_attempt_at"
        const val KEY_SUCCESS = "cellwatch.sync.last_success_at"
        const val KEY_LAST_COUNT = "cellwatch.sync.last_uploaded_count"
        const val KEY_TOTAL_COUNT = "cellwatch.sync.total_uploaded_count"
        const val KEY_FAILED = "cellwatch.sync.last_attempt_failed"
    }
}
