package edu.gatech.cc.cellwatch.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.gatech.cc.cellwatch.CellWatchApp
import edu.gatech.cc.cellwatch.data.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import java.io.FileDescriptor
import java.io.FileOutputStream

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

    fun exportData(fd: FileDescriptor) {
        viewModelScope.launch {
            val validGroups = groups.value.filter { it.submission != null }
            val exports = validGroups.mapNotNull { it.toFccSubmissionExport() }

            val representative = validGroups.firstOrNull()?.submission

            val exportBundle = FccSubmissionExportBundle(
                contact = Contact(
                    name = representative?.contactName,
                    email = representative?.contactEmail,
                    phone = representative?.contactPhone
                ),
                submission_category = representative?.submission ?: "Consumer Challenge",
                submissions = exports
            )

            FileOutputStream(fd).use {
                Json { prettyPrint = true
                       encodeDefaults = true
                       explicitNulls = true  }.encodeToStream(exportBundle, it)
            }
        }
    }
}
