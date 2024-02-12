import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import edu.gatech.cc.cellwatch.R
import edu.gatech.cc.cellwatch.data.model.Measurement

class MeasurementAdapter(private val measurements: List<Measurement>) :
    RecyclerView.Adapter<MeasurementAdapter.MeasurementViewHolder>() {

    inner class MeasurementViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val headerLayout: LinearLayout = view.findViewById(R.id.headerLayout)
        val detailsLayout: LinearLayout = view.findViewById(R.id.detailsLayout)
        val dateTextView: TextView = view.findViewById(R.id.dateTextView)
        val uploadSpeedTextView: TextView = view.findViewById(R.id.uploadSpeedTextView)
        val downloadSpeedTextView: TextView = view.findViewById(R.id.downloadSpeedTextView)
        val locationTextView: TextView = view.findViewById(R.id.locationTextView)
        val carrierTextView: TextView = view.findViewById(R.id.carrierTextView)
        val typeTextView: TextView = view.findViewById(R.id.typeTextView)
        val technologyTextView: TextView = view.findViewById(R.id.technologyTextView)
        val latencyTextView: TextView = view.findViewById(R.id.latencyTextView)
        val roamingTextView: TextView = view.findViewById(R.id.roamingTextView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MeasurementViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_measurement, parent, false)
        return MeasurementViewHolder(view)
    }

    override fun onBindViewHolder(holder: MeasurementViewHolder, position: Int) {
        val measurement = measurements[position]
        //TODO Replace with actual measurement data
        /*
        Something like


        holder.locationTextView.text = "Location: ${measurement.locations?.firstOrNull()?.description ?: "N/A"}"
        holder.carrierTextView.text = "Carrier: ${measurement.provider ?: "N/A"}"
        holder.typeTextView.text = "Type: ${measurement.type}"
        ...
         */

        //Outer
        holder.dateTextView.text = measurement.timestamp.toString()
        holder.uploadSpeedTextView.text = "23 Mbps"
        holder.downloadSpeedTextView.text = "77 Mbps"

        //Inner (shown on click)
        holder.locationTextView.text = "Location: Test Location"
        holder.carrierTextView.text = "Carrier: Test Carrier"
        holder.typeTextView.text = "Type: Test Type"
        holder.technologyTextView.text = "Technology: Test Tech"
        holder.latencyTextView.text = "Latency: test ms"
        holder.roamingTextView.text = "Roaming: Yes/no"


        holder.headerLayout.setOnClickListener {
            if (holder.detailsLayout.visibility == View.VISIBLE) {
                holder.detailsLayout.visibility = View.GONE
            } else {
                holder.detailsLayout.visibility = View.VISIBLE
            }
        }

    }

    override fun getItemCount() = measurements.size
}

