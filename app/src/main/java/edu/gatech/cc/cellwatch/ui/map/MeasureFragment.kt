package edu.gatech.cc.cellwatch.ui.map

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import edu.gatech.cc.cellwatch.CellWatchApp
import edu.gatech.cc.cellwatch.R
import edu.gatech.cc.cellwatch.core.util.Log
import edu.gatech.cc.cellwatch.data.model.Measurement
import edu.gatech.cc.cellwatch.data.model.MeasurementGroup
import edu.gatech.cc.cellwatch.databinding.FragmentMeasureBinding
import edu.gatech.cc.cellwatch.domain.fcc.MeasurementManager
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant

class MeasureFragment : Fragment() {
    private val TAG = this::class.simpleName
    private val measurementRepository = CellWatchApp.measurementRepository
    private val fccSubmissionRepository = CellWatchApp.fccSubmissionRepository
    private lateinit var binding: FragmentMeasureBinding
    private lateinit var model: MeasurementViewModel

    interface MeasureFragmentInteractionListener {
        fun onTakeAnotherMeasurementPressed()
        fun onBackToMapPressed()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentMeasureBinding.inflate(inflater, container, false)
        binding.takeAnotherButton.visibility = View.GONE
        binding.backToMapButton.visibility = View.GONE
        model = ViewModelProvider(requireActivity())[MeasurementViewModel::class.java]

        val interactionListener = if (context is MeasureFragmentInteractionListener) {
            context as MeasureFragmentInteractionListener
        } else {
            throw RuntimeException(context.toString() + " must implement MeasureFragmentInteractionListener")
        }
        binding.takeAnotherButton.setOnClickListener { interactionListener.onTakeAnotherMeasurementPressed() }
        binding.backToMapButton.setOnClickListener { interactionListener.onBackToMapPressed() }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        runTestSequence()
    }

    private fun runTestSequence(failIfNotOnCellular: Boolean = true) {
        viewLifecycleOwner.lifecycleScope.launch {
            var group = MeasurementGroup(null, null, null, null)
            model.group = group
            binding.item.setMeasurementGroup(group)
            binding.progressBar.visibility = View.VISIBLE

            try {
                group = MeasurementManager.runTestSequence(
                    model.inVehicle,
                    { handleLocateStart() },
                    { },
                    { handleLatencyStart() },
                    { handleLatencyComplete(it) },
                    { handleDownloadStart() },
                    { handleThroughputComplete(it) },
                    { handleUploadStart() },
                    { handleThroughputComplete(it) },
                    failIfNotOnCellular = failIfNotOnCellular,
                )
                model.group = group
                handleMeasurementComplete(group)
            } catch (e: MeasurementManager.NotOnCellularException) {
                handleNotOnCellular()
            } catch (e: Exception) {
                Log.e(TAG, "unexpected error running test sequence", e)
                handleMeasurementComplete(null)
            } finally {
                binding.progressBar.visibility = View.INVISIBLE
            }
        }
    }

    private fun handleLocateStart() {
       binding.header.setText(R.string.finding_server)
    }

    private fun handleLatencyStart() {
        binding.header.setText(R.string.measuring_latency)
    }

    private fun handleLatencyComplete(m: Measurement) {
        val group = MeasurementGroup(m, model.group?.download, model.group?.upload, model.group?.submission)
        model.group = group
        binding.item.setMeasurementGroup(group)
    }

    private fun handleDownloadStart() {
        binding.header.setText(R.string.measuring_download)
    }

    private fun handleUploadStart() {
        binding.header.setText(R.string.measuring_upload)
    }

    private fun handleThroughputComplete(m: Measurement) {
        val group = when (m.type) {
            "download" -> MeasurementGroup(model.group?.latency, m, model.group?.upload, model.group?.submission)
            "upload" -> MeasurementGroup(model.group?.latency, model.group?.download, m, model.group?.submission)
            else -> throw RuntimeException("throughput complete called with non-throughput measurement: ${m.type}")
        }
        model.group = group
        binding.item.setMeasurementGroup(group)
    }

    private fun handleNotOnCellular() {
        AlertDialog.Builder(context)
            .setMessage(R.string.not_cellular_warning)
            .setPositiveButton(R.string.not_cellular_proceed) { dialog, _ ->
                dialog.dismiss()
                runTestSequence(false)
            }
            .setNegativeButton(R.string.cancel) { dialog, _ -> dialog.dismiss()}
            .show()
    }

    private fun handleMeasurementComplete(group: MeasurementGroup?) {
        binding.progressBar.visibility = View.GONE
        binding.takeAnotherButton.visibility = View.VISIBLE
        binding.backToMapButton.visibility = View.VISIBLE

        if (group == null) {
            binding.header.setText(R.string.measurement_failed)
        } else {
            binding.header.setText(R.string.measurement_complete)
            binding.item.setMeasurementGroup(group)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                binding.item.updateUploadTime(uploadMeasurements())
            } catch (t: Throwable) {
                Log.e(TAG, "failed to set uploaded text", t)
            }
        }
    }

    private suspend fun uploadMeasurements(): Instant? {
        return try {
            val uploadTime = measurementRepository.uploadMeasurements()
            fccSubmissionRepository.uploadFccSubmissions()
            uploadTime
        } catch (e: Exception) {
            Log.d(TAG, "failed to upload measurements and submission", e)
            null
        }
    }
}