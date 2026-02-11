package edu.gatech.cc.cellwatch.androidtestapp.sync

import edu.gatech.cc.cellwatch.domain.model.MeasurementGroup
import edu.gatech.cc.cellwatch.domain.sync.SyncAllReport
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
    private val flow: LegacySharedSyncFlow,
) {
    private val _state = MutableStateFlow(AndroidTestSyncDriverState())
    val state: StateFlow<AndroidTestSyncDriverState> = _state.asStateFlow()

    suspend fun runMapStartSync(): SyncAllReport? {
        return runCatching { flow.onMapStartSync() }
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
        return runCatching { flow.onMeasurementCompleteSync(group) }
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
}
