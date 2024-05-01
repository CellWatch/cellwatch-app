package edu.gatech.cc.cellwatch.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import edu.gatech.cc.cellwatch.R
import edu.gatech.cc.cellwatch.domain.map.managers.H3Manager
import kotlinx.coroutines.launch
import okhttp3.internal.toHexString

class MeasurementListBottomSheetFragment : BottomSheetDialogFragment() {

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
            val adapter = MeasurementAdapter()
            recyclerView.adapter = adapter
            viewLifecycleOwner.lifecycleScope.launch {
                val groups = H3Manager.getMeasurementGroupsAssociatedWithH3Address(it, H3Manager.getH3ResolutionFromAddress(it))
                adapter.setGroups(groups)
            }
        }

        val textViewTitle: TextView = view.findViewById(R.id.tvTitle)
        val tvTitleText = getString(R.string.hex_index, h3Address?.toHexString()?.lowercase())
        textViewTitle.text = tvTitleText
    }
}