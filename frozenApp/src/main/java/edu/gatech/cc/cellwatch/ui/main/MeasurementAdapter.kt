package edu.gatech.cc.cellwatch.ui.main

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import edu.gatech.cc.cellwatch.R
import edu.gatech.cc.cellwatch.data.model.MeasurementGroup
import edu.gatech.cc.cellwatch.data.model.CollectionMode

class MeasurementAdapter() :
    RecyclerView.Adapter<MeasurementAdapter.MeasurementViewHolder>() {

    private var groups = listOf<MeasurementGroup>()
    private var hexAddress: Long? = null

    fun setData(g: Collection<MeasurementGroup>, hexAddr: Long? = null) {
        groups = g.sortedByDescending {
            it.latency?.timestamp ?: it.download?.timestamp ?: it.upload ?.timestamp
        }
        hexAddress = hexAddr
        notifyDataSetChanged()
    }

    inner class MeasurementViewHolder(val item: MeasurementGroupItem) : RecyclerView.ViewHolder(item)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MeasurementViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_measurement_history, parent, false)
        if (view !is MeasurementGroupItem) {
            throw RuntimeException("expected MeasurementGroupItem, got $view")
        }
        return MeasurementViewHolder(view)
    }

    override fun onBindViewHolder(holder: MeasurementViewHolder, position: Int) {
        val group = groups[position]
        holder.item.setData(group, displayedHexAddress = hexAddress)

        if(group.submission == null){
            holder.item.setMeasurementTypeText("Test")
        } else {
            holder.item.setItemBackground(R.drawable.measure_result_date_challenge)
            holder.item.setIcon(R.drawable.fa_circle_nodes_solid)
            holder.item.setMeasurementTypeText("Challenge")
        }

    }

    override fun getItemCount() = groups.size
}

