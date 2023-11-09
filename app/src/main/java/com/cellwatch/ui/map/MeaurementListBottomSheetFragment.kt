package com.cellwatch.ui.map

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cellwatch.R
import com.cellwatch.data.model.Measurement
import com.cellwatch.domain.map.managers.H3Manager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class MeasurementListBottomSheetFragment : BottomSheetDialogFragment() {

    private var measurements: List<Measurement>? = null
    private var h3Address: Long? = null

    companion object {
        private const val ARG_MEASUREMENT_ID = "arg_measurement_id"

        fun newInstance(measurementId: Long): MeasurementListBottomSheetFragment {
            val fragment = MeasurementListBottomSheetFragment()
            val args = Bundle()
            args.putLong(ARG_MEASUREMENT_ID, measurementId)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        h3Address = arguments?.getLong(ARG_MEASUREMENT_ID)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.map_bottom_sheet_layout, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val recyclerView: RecyclerView = view.findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(context)

        h3Address?.let {
            // Use the measurementId to fetch the associated measurements
            this.measurements = fetchMeasurements(it)
            recyclerView.adapter = MeasurementAdapter(measurements!!)
        }
    }
    private fun fetchMeasurements(id: Long): List<Measurement> {
        return H3Manager.getMeasurementsAssociatedWithH3Address(id, H3Manager.getH3ResolutionFromAddress(id))
    }
}

class MeasurementAdapter(private val measurements: List<Measurement>) : RecyclerView.Adapter<MeasurementAdapter.MeasurementViewHolder>() {
    class MeasurementViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textViewName: TextView = view.findViewById(R.id.textViewName)
        val textViewValue: TextView = view.findViewById(R.id.textViewValue)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MeasurementViewHolder {
        // Create a new view, which defines the UI of the list item
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.map_bottom_sheet_measurement_item, parent, false)

        return MeasurementViewHolder(view)
    }

    override fun getItemCount() = measurements.size
    override fun onBindViewHolder(holder: MeasurementViewHolder, position: Int) {
        val measurement = measurements[position
        // Set other attributes from measurement to the view holder
    }
}


