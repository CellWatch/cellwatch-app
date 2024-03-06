package edu.gatech.cc.cellwatch.ui.map

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import edu.gatech.cc.cellwatch.R
import edu.gatech.cc.cellwatch.core.util.Log
import edu.gatech.cc.cellwatch.data.model.MeasurementGroup
import kotlinx.coroutines.launch

class MeasurementAdapter(viewLifecycleOwner: LifecycleOwner, getMeasurementGroups: suspend () -> List<MeasurementGroup>) :
    RecyclerView.Adapter<MeasurementAdapter.MeasurementViewHolder>() {

    private var groups = listOf<MeasurementGroup>()

    init {
        viewLifecycleOwner.lifecycleScope.launch {
            Log.d("MeasurementAdapter", "getting measurements")
            groups = getMeasurementGroups().sortedByDescending {
                it.latency?.timestamp ?: it.download?.timestamp ?: it.upload ?.timestamp
            }
            Log.d("MeasurementAdapter", "got ${groups.size} measurements")
            notifyDataSetChanged()
        }
    }

    inner class MeasurementViewHolder(val item: MeasurementItem) : RecyclerView.ViewHolder(item)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MeasurementViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_measurement_history, parent, false)
        if (view !is MeasurementItem) {
            throw RuntimeException("expected MeasurementItem, got $view")
        }
        return MeasurementViewHolder(view)
    }

    override fun onBindViewHolder(holder: MeasurementViewHolder, position: Int) {
        val group = groups[position]
        holder.item.setMeasurementGroup(group)
    }

    override fun getItemCount() = groups.size
}

