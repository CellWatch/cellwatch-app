package edu.gatech.cc.cellwatch.ui.main

import androidx.lifecycle.ViewModel

class MeasurementGroupItemViewModel: ViewModel() {
    val expandedGroupIds = mutableSetOf<String>()
}