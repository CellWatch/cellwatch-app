package edu.gatech.cc.cellwatch.ui.map

import androidx.lifecycle.ViewModel
import edu.gatech.cc.cellwatch.data.model.Measurement

class MeasurementViewModel: ViewModel() {
    var inVehicle = false
    var latencyResult: Measurement? = null
    var downloadResult: Measurement? = null
    var uploadResult: Measurement? = null
}