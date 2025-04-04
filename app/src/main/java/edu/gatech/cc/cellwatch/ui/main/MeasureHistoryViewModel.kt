package edu.gatech.cc.cellwatch.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.gatech.cc.cellwatch.CellWatchApp
import edu.gatech.cc.cellwatch.data.model.FccExportBundle
import edu.gatech.cc.cellwatch.data.model.MeasurementGroup
import edu.gatech.cc.cellwatch.data.model.toFccSubmissionExport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import java.io.FileDescriptor
import java.io.FileOutputStream

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
        val data = groups.value
        val fccValid = data.filter { it.submission != null }
            .mapNotNull { it.toFccSubmissionExport() }

        val others = data.filter { it.submission == null }

        val export = FccExportBundle(fcc_valid = fccValid, others = others)

        FileOutputStream(fd).use {
            Json.encodeToStream(export, it)
        }
    }
}
