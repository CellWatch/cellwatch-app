package edu.gatech.cc.cellwatch.ui.main

import android.content.Intent
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

        binding.takeAnotherButton.setOnClickListener { model.reset() }
        binding.backToMapButton.setOnClickListener {
            startActivity(Intent(context, MapActivity::class.java))
            requireActivity().finish()
            model.reset()
        }

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
        progress: MeasureViewModel.MeasureProgress,
        uploadTime: Instant?,
    ) {
        binding.item.setMeasurementGroup(group ?: MeasurementGroup(null, null, null, null))
        val complete = when (progress) {
            MeasureViewModel.MeasureProgress.END,
            MeasureViewModel.MeasureProgress.ERROR -> true
            else -> false
        }

        binding.progressBar.isVisible = !complete
        binding.takeAnotherButton.isVisible = complete
        binding.backToMapButton.isVisible = complete

        binding.header.setText(when (progress) {
            MeasureViewModel.MeasureProgress.PRE,
            MeasureViewModel.MeasureProgress.START,
            MeasureViewModel.MeasureProgress.NOT_CELLULAR -> R.string.measuring
            MeasureViewModel.MeasureProgress.LOCATE -> R.string.finding_server
            MeasureViewModel.MeasureProgress.LATENCY -> R.string.measuring_latency
            MeasureViewModel.MeasureProgress.DOWNLOAD ->R.string.measuring_download
            MeasureViewModel.MeasureProgress.UPLOAD -> R.string.measuring_upload
            MeasureViewModel.MeasureProgress.END -> R.string.measurement_complete
            MeasureViewModel.MeasureProgress.ERROR -> R.string.measurement_failed
        })

        binding.item.updateUploadTime(uploadTime)
    }
}