package edu.gatech.cc.cellwatch.ui.map

import androidx.lifecycle.ViewModel
import edu.gatech.cc.cellwatch.data.model.Measurement
import edu.gatech.cc.cellwatch.data.model.MeasurementGroup

class MeasurementViewModel: ViewModel() {
    var inVehicle = false
    var group: MeasurementGroup? = null
}