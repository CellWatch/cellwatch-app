package edu.gatech.cc.cellwatch.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.gatech.cc.cellwatch.CellWatchApp
import edu.gatech.cc.cellwatch.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import java.io.OutputStream
import edu.gatech.cc.cellwatch.data.core.repositories.SettingsRepository

@Serializable
data class FccSubmissionExportBundle(
    val contact: Contact,
    val submission_category: String,
    val submissions: List<FccSubmissionExport>
)

@Serializable
data class Contact(
    val name: String? = null,
    val email: String? = null,
    val phone: String? = null
)

class MeasureHistoryViewModel : ViewModel() {
    private var _groups = MutableStateFlow(listOf<MeasurementGroup>())
    val groups: StateFlow<List<MeasurementGroup>> = _groups

    fun loadGroups() {
        viewModelScope.launch {
            val g = CellWatchApp.measurementRepository.getMeasurementGroups()
            _groups.update { g }
        }
    }

    /** Writes the FCC export JSON to [out]. The caller keeps [out] open until this returns. */
    suspend fun exportData(out: OutputStream) {
        val validGroups = groups.value.filter { it.submission != null }
        val exports = validGroups.mapNotNull { it.toFccSubmissionExport() }

        val representative = validGroups.firstOrNull()?.submission

        val exportBundle = FccSubmissionExportBundle(
            contact = Contact(
                name = CellWatchApp.settingsRepository.getName(),
                email = CellWatchApp.settingsRepository.getEmail(),
                phone = CellWatchApp.settingsRepository.getPhoneNumber()
            ),
            submission_category = representative?.submission ?: "Consumer Challenge",
            submissions = exports
        )

        withContext(Dispatchers.IO) {
            Json { prettyPrint = true
                   encodeDefaults = true
                   explicitNulls = true  }.encodeToStream(exportBundle, out)
        }
    }
}
