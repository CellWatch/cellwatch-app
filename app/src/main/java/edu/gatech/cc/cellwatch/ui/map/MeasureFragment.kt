package edu.gatech.cc.cellwatch.ui.map

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import edu.gatech.cc.cellwatch.R
import edu.gatech.cc.cellwatch.data.model.MeasurementGroup
import edu.gatech.cc.cellwatch.databinding.FragmentMeasureBinding
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant

class MeasureFragment : Fragment() {
    private lateinit var binding: FragmentMeasureBinding
    private lateinit var model: MeasureViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentMeasureBinding.inflate(inflater, container, false)
        model = ViewModelProvider(requireActivity())[MeasureViewModel::class.java]

        binding.takeAnotherButton.setOnClickListener { model.prepareForMeasurement() }
        binding.backToMapButton.setOnClickListener { model.stopMeasuring() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.state.collect {
                    handleStateUpdate(it.results, it.progress, it.uploadTime)
                }
            }
        }

        return binding.root
    }

    private fun handleStateUpdate(
        group: MeasurementGroup?,
        progress: MeasureViewModel.MeasureProgress?,
        uploadTime: Instant?,
    ) {
        binding.item.setMeasurementGroup(group ?: MeasurementGroup(null, null, null, null))
        val complete = when (progress) {
            MeasureViewModel.MeasureProgress.SAVING,
            MeasureViewModel.MeasureProgress.END,
            MeasureViewModel.MeasureProgress.ERROR -> true
            else -> false
        }

        binding.progressBar.isVisible = !complete
        binding.takeAnotherButton.isVisible = complete
        binding.backToMapButton.isVisible = complete

        when (progress) {
            null,
            MeasureViewModel.MeasureProgress.PRE,
            MeasureViewModel.MeasureProgress.START -> {} // ignore
            MeasureViewModel.MeasureProgress.NOT_CELLULAR -> {
                AlertDialog.Builder(context)
                    .setMessage(R.string.not_cellular_warning)
                    .setPositiveButton(R.string.not_cellular_proceed) { dialog, _ ->
                        dialog.dismiss()
                        model.startMeasurement()
                    }
                    .setNegativeButton(R.string.cancel) { dialog, _ ->
                        dialog.dismiss()
                        model.prepareForMeasurement()
                    }
                    .show()
            }
            MeasureViewModel.MeasureProgress.LOCATE -> binding.header.setText(R.string.finding_server)
            MeasureViewModel.MeasureProgress.LATENCY -> binding.header.setText(R.string.measuring_latency)
            MeasureViewModel.MeasureProgress.DOWNLOAD -> binding.header.setText(R.string.measuring_download)
            MeasureViewModel.MeasureProgress.UPLOAD -> binding.header.setText(R.string.measuring_upload)
            MeasureViewModel.MeasureProgress.SAVING -> binding.header.setText(R.string.measurement_complete)
            MeasureViewModel.MeasureProgress.END -> binding.item.updateUploadTime(uploadTime)
            MeasureViewModel.MeasureProgress.ERROR -> binding.header.setText(R.string.measurement_failed)
        }
    }
}