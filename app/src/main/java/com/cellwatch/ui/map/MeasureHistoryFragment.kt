package com.cellwatch.ui.map

import MeasurementAdapter
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.cellwatch.R
import com.cellwatch.data.model.Measurement

class MeasureHistoryFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_measurehistory, container, false)

        val recyclerView: RecyclerView = view.findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(context)

        val adapter = MeasurementAdapter(getMeasurements())
        recyclerView.adapter = adapter

        return view
    }


    private fun getMeasurements(): List<Measurement> {
        //TODO Implement this
        return listOf(
            Measurement(type = "Test1"),
            Measurement(type = "Test2")
        )
    }
}
