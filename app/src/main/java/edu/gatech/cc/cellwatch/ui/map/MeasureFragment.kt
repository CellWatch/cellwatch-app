package edu.gatech.cc.cellwatch.ui.map

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import edu.gatech.cc.cellwatch.R

class MeasureFragment : Fragment() {
    private var progress = 0
    var buttonIncrement: Button? = null
    var buttonDecrement: Button? = null
    var progressBar: ProgressBar? = null
    var textView: TextView? = null
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val rootView = inflater.inflate(R.layout.fragment_measure, container, false)
        buttonDecrement = rootView.findViewById<View>(R.id.button_decr) as Button
        buttonIncrement = rootView.findViewById<View>(R.id.button_incr) as Button
        progressBar = rootView.findViewById<View>(R.id.progress_bar) as ProgressBar
        textView = rootView.findViewById<View>(R.id.text_view_progress) as TextView

        // when clicked on buttonIncrement progress is increased by 10%
        buttonIncrement!!.setOnClickListener { // if progress is less than or equal
            // to 90% then only it can be increased
            if (progress <= 90) {
                progress += 10
                updateProgressBar()
            }
        }

        // when clicked on buttonIncrement progress is decreased by 10%
        buttonDecrement!!.setOnClickListener { // If progress is greater than
            // 10% then only it can be decreased
            if (progress >= 10) {
                progress -= 10
                updateProgressBar()
            }
        }
        return rootView
    }

    // updateProgressBar() method sets
    // the progress of ProgressBar in text
    private fun updateProgressBar() {
        progressBar!!.progress = progress
        textView!!.text = progress.toString()
    }
}