package edu.gatech.cc.cellwatch.ui.map

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
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
    private lateinit var binding: FragmentMeasureBinding
    private lateinit var model: MeasurementViewModel

    interface MeasureFragmentInteractionListener {
        fun onMeasurementComplete()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentMeasureBinding.inflate(inflater, container, false)
        binding.progressBar.visibility = View.INVISIBLE
        model = ViewModelProvider(requireActivity())[MeasurementViewModel::class.java]
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        runTestSequence()
    }

    private fun runTestSequence(failIfNotOnCellular: Boolean = true) {
        viewLifecycleOwner.lifecycleScope.launch {
            model.latencyResult = null
            model.downloadResult = null
            model.uploadResult = null
            binding.latencyResult.text = ""
            binding.downloadResult.text = ""
            binding.uploadResult.text = ""
            binding.progressBar.visibility = View.VISIBLE

            try {
                MeasurementManager.runTestSequence(
                    model.inVehicle,
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
                handleMeasurementComplete()
            } catch (e: MeasurementManager.NotOnCellularException) {
                handleNotOnCellular()
            } catch (e: Exception) {
                Log.e(TAG, "unexpected error running test sequence", e)
                handleMeasurementComplete()
            } finally {
                binding.progressBar.visibility = View.INVISIBLE
            }
        }
    }

    private fun handleLocateStart() {
       binding.header.setText(R.string.finding_server)
    }

    private fun handleLocateComplete() {
        // do nothing
    }

    private fun handleLatencyStart() {
        binding.header.setText(R.string.measuring_latency)
        binding.latencyResult.setText(R.string.running)
    }

    private fun handleLatencyComplete(m: Measurement) {
        model.latencyResult = m
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
        when (m.type) {
            "download" -> model.downloadResult = m
            "upload" -> model.uploadResult = m
            else -> throw RuntimeException("unexpected measurement type ${m.type}")
        }
        val activeMetrics = ThroughputMetrics(m.uploadDownloadData?.bytes ?: 0, m.uploadDownloadData?.duration ?: 0)
        val speedMbps = (activeMetrics.bytesPerSec * 8 / 1e6).roundToInt()
        if (m.success == true) {
            content.text = getString(R.string.speed_mbps, speedMbps)
        } else {
            content.setText(R.string.failed)
        }
    }

    private fun handleNotOnCellular() {
        AlertDialog.Builder(context)
            .setMessage(R.string.not_cellular_warning)
            .setPositiveButton(R.string.not_cellular_proceed) { dialog, _ ->
                dialog.dismiss()
                runTestSequence(false)
            }
            .setNegativeButton(R.string.not_cellular_abort) { dialog, _ -> dialog.dismiss()}
            .show()
    }

    private fun handleMeasurementComplete() {
        val interactionListener = if (context is MeasureFragmentInteractionListener) {
            context as MeasureFragmentInteractionListener
        } else {
            throw RuntimeException(context.toString() + " must implement MeasureFragmentInteractionListener")
        }

        interactionListener.onMeasurementComplete()
    }
}