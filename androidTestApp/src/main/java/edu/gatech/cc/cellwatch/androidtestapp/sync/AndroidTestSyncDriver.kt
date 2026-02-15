package edu.gatech.cc.cellwatch.androidtestapp.sync

import edu.gatech.cc.cellwatch.domain.model.MeasurementGroup
import edu.gatech.cc.cellwatch.domain.sync.PendingSyncCounts
import edu.gatech.cc.cellwatch.domain.sync.RetryPendingSyncUseCase
import edu.gatech.cc.cellwatch.domain.sync.SyncAllReport
import edu.gatech.cc.cellwatch.domain.sync.SyncReport
import edu.gatech.cc.cellwatch.domain.sync.SyncRunStatus
import edu.gatech.cc.cellwatch.domain.sync.SyncRunSummary
import edu.gatech.cc.cellwatch.domain.sync.UploadTriggerUseCase
import edu.gatech.cc.cellwatch.domain.sync.GetPendingSyncCountsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.datetime.Instant

data class AndroidTestSyncDriverState(
    val lastReport: SyncAllReport? = null,
    val lastUploadTime: Instant? = null,
    val lastError: String? = null,
)

class AndroidTestSyncDriver(
    private val uploadTriggerUseCase: UploadTriggerUseCase,
    private val loadPendingCounts: (suspend () -> PendingSyncCounts)? = null,
    private val retryPendingSync: (suspend () -> SyncRunSummary)? = null,
) {
    constructor(
        uploadTriggerUseCase: UploadTriggerUseCase,
        pendingCountsUseCase: GetPendingSyncCountsUseCase,
        retryPendingSyncUseCase: RetryPendingSyncUseCase,
    ) : this(
        uploadTriggerUseCase = uploadTriggerUseCase,
        loadPendingCounts = { pendingCountsUseCase.execute() },
        retryPendingSync = { retryPendingSyncUseCase.execute() },
    )

    private val _state = MutableStateFlow(AndroidTestSyncDriverState())
    val state: StateFlow<AndroidTestSyncDriverState> = _state.asStateFlow()

    suspend fun runMapStartSync(): SyncAllReport? {
        return runCatching { uploadTriggerUseCase.onMapStart() }
            .onSuccess { report ->
                _state.update { old ->
                    old.copy(
                        lastReport = report,
                        lastError = null,
                    )
                }
            }
            .onFailure { e ->
                _state.update { old ->
                    old.copy(lastError = e.message ?: "sync failed")
                }
            }
            .getOrNull()
    }

    suspend fun runMeasurementCompleteSync(group: MeasurementGroup): Instant? {
        return runCatching { uploadTriggerUseCase.onMeasurementComplete(group) }
            .onSuccess { uploadTime ->
                _state.update { old ->
                    old.copy(
                        lastUploadTime = uploadTime,
                        lastError = null,
                    )
                }
            }
            .onFailure { e ->
                _state.update { old ->
                    old.copy(lastError = e.message ?: "sync failed")
                }
            }
            .getOrNull()
    }

    suspend fun getPendingSyncCounts(): PendingSyncCounts {
        return loadPendingCounts?.invoke() ?: PendingSyncCounts()
    }

    suspend fun runPendingSync(): SyncRunSummary? {
        return runCatching {
            retryPendingSync?.invoke()
                ?: SyncRunSummary(
                    status = SyncRunStatus.IDLE,
                    before = PendingSyncCounts(),
                    after = PendingSyncCounts(),
                    report = SyncAllReport(
                        measurements = SyncReport(),
                        submissions = SyncReport(),
                    ),
                    userMessage = "No pending uploads.",
                )
        }
            .onFailure { e ->
                _state.update { old ->
                    old.copy(lastError = e.message ?: "sync failed")
                }
            }
            .getOrNull()
    }
}
