package edu.gatech.cc.cellwatch.ui.map

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import edu.gatech.cc.cellwatch.CellWatchApp
import edu.gatech.cc.cellwatch.R
import edu.gatech.cc.cellwatch.core.util.Log
import edu.gatech.cc.cellwatch.data.model.Measurement
import edu.gatech.cc.cellwatch.databinding.FragmentMeasureBinding
import edu.gatech.cc.cellwatch.domain.fcc.MeasurementManager
import edu.gatech.cc.cellwatch.domain.fcc.ThroughputMetrics
import edu.gatech.cc.cellwatch.ui.measurement.viewmodels.MeasurementViewModel
import edu.gatech.cc.cellwatch.ui.measurement.viewmodels.MeasurementViewModelFactory
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class MeasureFragment : Fragment() {
    private val TAG = this::class.simpleName
    private var progress = 0
    private lateinit var binding: FragmentMeasureBinding
    private var locateComplete = false
    private var latencyComplete = false
    private var downloadComplete = false
    private var uploadComplete = false

    private val measurementViewModel: MeasurementViewModel by activityViewModels() {
        MeasurementViewModelFactory(CellWatchApp.measurementRepository)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentMeasureBinding.inflate(inflater, container, false)
        //val rootView = inflater.inflate(R.layout.fragment_measure, container, false)

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

        val inVehicle = arguments?.getBoolean("inVehicle") ?: throw RuntimeException("missing inVehicle arg")
        binding.buttonMeasure.setOnClickListener { runTestSequence(inVehicle) }

        //return rootView
        return binding.root
    }

    // updateProgressBar() method sets
    // the progress of ProgressBar in text
    private fun updateProgressBar() {
        binding.progressBar.progress = progress
        binding.textViewProgress.text = progress.toString()
    }

    private fun runTestSequence(inVehicle: Boolean, failIfNotOnCellular: Boolean = true) {
        viewLifecycleOwner.lifecycleScope.launch {
            locateComplete = false
            latencyComplete = false
            downloadComplete = false
            uploadComplete = false
            binding.latencyContent.text = ""
            binding.downloadContent.text = ""
            binding.uploadContent.text = ""

            try {
                MeasurementManager.runTestSequence(
                    inVehicle,
                    { handleLocateStart() },
                    { handleLocateComplete() },
                    { handleLatencyStart() },
                    { handleLatencyComplete(it) },
                    { handleDownloadStart() },
                    { handleThroughputComplete(binding.downloadContent, it) },
                    { handleUploadStart() },
                    { handleThroughputComplete(binding.uploadContent, it) },
                    failIfNotOnCellular = failIfNotOnCellular,
                )
                binding.textStatus.setText(R.string.measurement_complete)
            } catch (e: MeasurementManager.NotOnCellularException) {
                handleNotOnCellular(inVehicle)
            } catch (e: Exception) {
                Log.e(TAG, "unexpected error running test sequence", e)

                if (locateComplete) {
                    binding.textStatus.setText(R.string.error_running_measurement)
                } else {
                    binding.textStatus.setText(R.string.failed_find_server)
                }

                if (!latencyComplete) {
                    binding.latencyContent.setText(R.string.failed)
                }

                if (!downloadComplete) {
                    binding.downloadContent.setText(R.string.failed)
                }

                if (!uploadComplete) {
                    binding.uploadContent.setText(R.string.failed)
                }
            }
        }
    }

    private fun handleLocateStart() {
       binding.textStatus.setText(R.string.finding_server)
    }

    private fun handleLocateComplete() {
        locateComplete = true
        binding.textStatus.setText(R.string.found_server)
    }

    private fun handleLatencyStart() {
        binding.textStatus.setText(R.string.measuring_latency)
        binding.latencyContent.setText(R.string.running)
    }

    private fun handleLatencyComplete(m: Measurement) {
        val rttMillis = ((m.latencyData?.rtt ?: 0) / 1e3).roundToInt()
        if (m.success == true) {
            binding.latencyContent.text = getString(R.string.latency_ms, rttMillis)
        } else {
            binding.latencyContent.setText(R.string.failed)
        }
    }

    private fun handleDownloadStart() {
        binding.textStatus.setText(R.string.measuring_download)
        binding.downloadContent.setText(R.string.running)
    }

    private fun handleUploadStart() {
        binding.textStatus.setText(R.string.measuring_upload)
        binding.uploadContent.setText(R.string.running)
    }

    private fun handleThroughputComplete(content: TextView, m: Measurement) {
        val activeMetrics = ThroughputMetrics(m.uploadDownloadData?.bytes ?: 0, m.uploadDownloadData?.duration ?: 0)
        val speedMbps = (activeMetrics.bytesPerSec * 8 / 1e6).roundToInt()
        if (m.success == true) {
            content.text = getString(R.string.speed_mbps, speedMbps)
        } else {
            content.setText(R.string.failed)
        }
    }

    private fun handleNotOnCellular(inVehicle: Boolean) {
        binding.textStatus.setText(R.string.not_on_cellular)

        AlertDialog.Builder(context)
            .setMessage(R.string.not_cellular_warning)
            .setPositiveButton(R.string.not_cellular_proceed) { dialog, _ ->
                dialog.dismiss()
                runTestSequence(inVehicle, false)
            }
            .setNegativeButton(R.string.not_cellular_abort) { dialog, _ -> dialog.dismiss()}
            .show()
    }
}