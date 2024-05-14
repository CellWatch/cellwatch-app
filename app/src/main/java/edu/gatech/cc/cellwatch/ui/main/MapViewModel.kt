package edu.gatech.cc.cellwatch.ui.main

import androidx.lifecycle.ViewModel
import edu.gatech.cc.cellwatch.data.model.MeasurementGroup

class MapViewModel: ViewModel() {
    var selectedGroups: Collection<MeasurementGroup>? = null
}