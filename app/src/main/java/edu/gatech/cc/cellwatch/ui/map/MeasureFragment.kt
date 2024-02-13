package edu.gatech.cc.cellwatch.ui.map

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import edu.gatech.cc.cellwatch.R
import edu.gatech.cc.cellwatch.core.util.Log
import edu.gatech.cc.cellwatch.data.model.Measurement
import edu.gatech.cc.cellwatch.databinding.FragmentMeasureBinding
import edu.gatech.cc.cellwatch.domain.fcc.MeasurementManager
import edu.gatech.cc.cellwatch.domain.fcc.ThroughputMetrics
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class MeasureFragment : Fragment() {
    private val TAG = this::class.simpleName
    private var progress = 0
    private lateinit var binding: FragmentMeasureBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentMeasureBinding.inflate(inflater, container, false)

        // when clicked on buttonIncrement progress is increased by 10%
        binding.buttonIncr.setOnClickListener { // if progress is less than or equal
            // to 90% then only it can be increased
            if (progress <= 90) {
                progress += 10
                updateProgressBar()
            }
        }

        // when clicked on buttonIncrement progress is decreased by 10%
        binding.buttonDecr.setOnClickListener { // If progress is greater than
            // 10% then only it can be decreased
            if (progress >= 10) {
                progress -= 10
                updateProgressBar()
            }
        }

        binding.buttonMeasure.setOnClickListener {
            runTestSequence()
        }

        return binding.root
    }

    // updateProgressBar() method sets
    // the progress of ProgressBar in text
    private fun updateProgressBar() {
        binding.progressBar.progress = progress
        binding.textViewProgress.text = progress.toString()
    }

    private fun runTestSequence(failIfNotOnCellular: Boolean = true) {
        viewLifecycleOwner.lifecycleScope.launch {
            binding.latencyContent.text = ""
            binding.downloadContent.text = ""
            binding.uploadContent.text = ""

            try {
                MeasurementManager.runTestSequence(
                    { binding.textStatus.text = "finding server..." },
                    { r -> binding.textStatus.text = "found server $r" },
                    { handleLatencyStart() },
                    { handleLatencyComplete(it) },
                    { handleDownloadStart() },
                    { handleThroughputComplete(binding.downloadContent, it) },
                    { handleUploadStart() },
                    { handleThroughputComplete(binding.uploadContent, it) },
                    failIfNotOnCellular = failIfNotOnCellular,
                )
                binding.textStatus.text = "measurement complete"
            } catch (e: MeasurementManager.NotOnCellularException) {
                handleNotOnCellular()
            } catch (e: Exception) {
                Log.e(TAG, "unexpected error running test sequence", e)

                if (binding.textStatus.text == "" || binding.textStatus.text == "finding server...") {
                    binding.textStatus.text == "failed to find server"
                } else {
                    binding.textStatus.text == "error running measurement"
                }

                if (binding.latencyContent.text == "" || binding.latencyContent.text == "running...") {
                    binding.latencyContent.text = "failed"
                }

                if (binding.downloadContent.text == "" || binding.downloadContent.text == "running...") {
                    binding.downloadContent.text = "failed"
                }

                if (binding.uploadContent.text == "" || binding.uploadContent.text == "running...") {
                    binding.uploadContent.text = "failed"
                }
            }
        }
    }

    private fun handleLatencyStart() {
        binding.textStatus.text = "measuring latency"
        binding.latencyContent.text = "running..."
    }

    private fun handleLatencyComplete(m: Measurement) {
        val rttMillis = (m.latencyData?.rtt ?: 0) / 1e3
        binding.latencyContent.text = if (m.success == true) "$rttMillis ms" else "failed"
    }

    private fun handleDownloadStart() {
        binding.textStatus.text = "measuring download speed"
        binding.downloadContent.text = "running..."
    }

    private fun handleUploadStart() {
        binding.textStatus.text = "measuring upload speed"
        binding.uploadContent.text = "running..."
    }

    private fun handleThroughputComplete(content: TextView, m: Measurement) {
        val activeMetrics = ThroughputMetrics(m.uploadDownloadData?.bytes ?: 0, m.uploadDownloadData?.duration ?: 0)
        val speedMbps = (activeMetrics.bytesPerSec * 8 / 1e6).roundToInt()
        content.text = if (m.success == true) "$speedMbps Mbps" else "failed"
    }

    private fun handleNotOnCellular() {
        binding.textStatus.text = "not on cellular"

        AlertDialog.Builder(context)
            .setMessage(R.string.not_cellular_warning)
            .setPositiveButton(R.string.not_cellular_proceed) { dialog, _ ->
                dialog.dismiss()
                runTestSequence(false)
            }
            .setNegativeButton(R.string.not_cellular_abort) { dialog, _ -> dialog.dismiss()}
            .show()
    }
}