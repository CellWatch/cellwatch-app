package edu.gatech.cc.cellwatch.ui.map

import android.graphics.PorterDuff
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import edu.gatech.cc.cellwatch.R
import edu.gatech.cc.cellwatch.databinding.FragmentPreMeasureBinding

class PreMeasureFragment: Fragment() {
    private lateinit var binding: FragmentPreMeasureBinding
    private var inVehicle = false
        set(v) {
            field = v

            binding.stationary.isSelected = !v
            binding.stationaryCheck.isVisible = !v
            if (v) {
                binding.stationaryIcon.clearColorFilter()
            } else {
                binding.stationaryIcon.setColorFilter(resources.getColor(R.color.cwbuttongreen, null))
            }

            binding.moving.isSelected = v
            binding.movingCheck.isVisible = v
            if (v) {
                binding.movingIcon.setColorFilter(resources.getColor(R.color.cwbuttongreen, null))
            } else {
                binding.movingIcon.clearColorFilter()
            }
        }

    interface PreMeasureFragmentInteractionListener {
        fun onGoButtonPressed(inVehicle: Boolean)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentPreMeasureBinding.inflate(inflater, container, false)

        binding.stationary.setOnClickListener { inVehicle = false }
        binding.moving.setOnClickListener { inVehicle = true }
        inVehicle = false

        val interactionListener = if (context is PreMeasureFragmentInteractionListener) {
            context as PreMeasureFragmentInteractionListener
        } else {
            throw RuntimeException(context.toString() + " must implement OnMapFragmentInteractionListener")
        }

        binding.go.setOnClickListener { interactionListener.onGoButtonPressed(inVehicle) }

        return binding.root
    }
}