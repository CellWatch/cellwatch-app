package edu.gatech.cc.cellwatch.ui.map

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
    private var locateComplete = false
    private var latencyComplete = false
    private var downloadComplete = false
    private var uploadComplete = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentMeasureBinding.inflate(inflater, container, false)
        binding.progressBar.visibility = View.INVISIBLE
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val inVehicle = arguments?.getBoolean("inVehicle") ?: throw RuntimeException("missing inVehicle arg")
        runTestSequence(inVehicle)
    }

    private fun runTestSequence(inVehicle: Boolean, failIfNotOnCellular: Boolean = true) {
        viewLifecycleOwner.lifecycleScope.launch {
            locateComplete = false
            latencyComplete = false
            downloadComplete = false
            uploadComplete = false
            binding.latencyResult.text = ""
            binding.downloadResult.text = ""
            binding.uploadResult.text = ""
            binding.progressBar.visibility = View.VISIBLE

            try {
                MeasurementManager.runTestSequence(
                    inVehicle,
                    { handleLocateStart() },
                    { handleLocateComplete() },
                    { handleLatencyStart() },
                    { handleLatencyComplete(it) },
                    { handleDownloadStart() },
                    { handleThroughputComplete(binding.downloadResult, it) },
                    { handleUploadStart() },
                    { handleThroughputComplete(binding.uploadResult, it) },
                    failIfNotOnCellular = failIfNotOnCellular,
                )
                binding.header.text = "Measurement complete" // TODO: move to post-measurement screen
            } catch (e: MeasurementManager.NotOnCellularException) {
                handleNotOnCellular(inVehicle)
            } catch (e: Exception) {
                Log.e(TAG, "unexpected error running test sequence", e)
                binding.header.text = "Error!" // TODO: move to post-measurement screen

                if (!latencyComplete) {
                    binding.latencyResult.setText(R.string.failed)
                }

                if (!downloadComplete) {
                    binding.downloadResult.setText(R.string.failed)
                }

                if (!uploadComplete) {
                    binding.uploadResult.setText(R.string.failed)
                }
            } finally {
                binding.progressBar.visibility = View.INVISIBLE
            }
        }
    }

    private fun handleLocateStart() {
       binding.header.setText(R.string.finding_server)
    }

    private fun handleLocateComplete() {
        locateComplete = true
    }

    private fun handleLatencyStart() {
        binding.header.setText(R.string.measuring_latency)
        binding.latencyResult.setText(R.string.running)
    }

    private fun handleLatencyComplete(m: Measurement) {
        val rttMillis = ((m.latencyData?.rtt ?: 0) / 1e3).roundToInt()
        if (m.success == true) {
            binding.latencyResult.text = getString(R.string.latency_ms, rttMillis)
        } else {
            binding.latencyResult.setText(R.string.failed)
        }
    }

    private fun handleDownloadStart() {
        binding.header.setText(R.string.measuring_download)
        binding.downloadResult.setText(R.string.running)
    }

    private fun handleUploadStart() {
        binding.header.setText(R.string.measuring_upload)
        binding.uploadResult.setText(R.string.running)
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