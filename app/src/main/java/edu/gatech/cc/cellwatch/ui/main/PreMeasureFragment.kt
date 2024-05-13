package edu.gatech.cc.cellwatch.ui.main

import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import edu.gatech.cc.cellwatch.CellWatchApp
import edu.gatech.cc.cellwatch.R
import edu.gatech.cc.cellwatch.data.model.CollectionMode
import edu.gatech.cc.cellwatch.databinding.FragmentPreMeasureBinding
import kotlinx.coroutines.launch

class PreMeasureFragment: Fragment() {
    private lateinit var binding: FragmentPreMeasureBinding
    private lateinit var model: MeasureViewModel
    private var inVehicle = false
        set(v) {
            field = v

            binding.stationary.isSelected = !v
            binding.stationaryCheck.isVisible = !v
            if (v) {
                binding.stationaryIcon.setColorFilter(resources.getColor(R.color.cw_grey, null))
            } else {
                binding.stationaryIcon.setColorFilter(resources.getColor(R.color.cw_green, null))
            }

            binding.moving.isSelected = v
            binding.movingCheck.isVisible = v
            if (v) {
                binding.movingIcon.setColorFilter(resources.getColor(R.color.cw_green, null))
            } else {
                binding.stationaryIcon.setColorFilter(resources.getColor(R.color.cw_grey, null))
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentPreMeasureBinding.inflate(inflater, container, false)
        model = ViewModelProvider(requireActivity())[MeasureViewModel::class.java]

        binding.stationary.setOnClickListener { model.setInVehicle(false) }
        binding.moving.setOnClickListener { model.setInVehicle(true) }
        binding.go.setOnClickListener { model.startMeasurement() }

        // enable clicking on link to show privacy policy
        binding.dataReminder.movementMethod = LinkMovementMethod.getInstance()

        inVehicle = false
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                val mode = CellWatchApp.settingsRepository.getCollectionMode()
                binding.stationaryOrMoving.isVisible = mode === CollectionMode.FCC_CHALLENGE
                binding.certifications.isVisible = mode === CollectionMode.FCC_CHALLENGE
                binding.testingWarning.isVisible = mode !== CollectionMode.FCC_CHALLENGE

                model.state.collect { inVehicle = it.inVehicle }
            }
        }

        return binding.root
    }
}