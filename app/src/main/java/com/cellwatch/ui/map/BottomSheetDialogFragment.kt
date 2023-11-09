package com.cellwatch.ui.map

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.cellwatch.R
import com.cellwatch.data.model.Measurement
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class MeasurementBottomSheetFragment : BottomSheetDialogFragment() {

    // You can pass the measurements via a factory method or a bundle
    var measurements: List<Measurement> = listOf() // Replace with your actual data type

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.map_bottom_sheet_layout, container, false)
    }

    companion object {
        fun newInstance(measurements: List<Measurement>): MeasurementBottomSheetFragment {
            val fragment = MeasurementBottomSheetFragment()
            fragment.measurements = measurements
            return fragment
        }
    }
}