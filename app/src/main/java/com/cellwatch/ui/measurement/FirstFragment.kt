package com.cellwatch.ui.measurement

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.cellwatch.R
import com.cellwatch.databinding.FragmentFirstBinding
import com.cellwatch.ui.measurement.viewmodels.MeasurementViewModel
import com.cellwatch.ui.measurement.viewmodels.MeasurementViewModelFactory
import com.github.anastr.speedviewlib.Gauge
import com.github.anastr.speedviewlib.SpeedView
import github.nisrulz.easydeviceinfo.base.*
import kotlinx.coroutines.launch

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class FirstFragment : Fragment() {
    private val TAG = this::class.simpleName
    private var _binding: FragmentFirstBinding? = null

    private val measurementViewModel: MeasurementViewModel by activityViewModels() {
        MeasurementViewModelFactory(com.cellwatch.CellWatchApp.measurementRepository)
    }

//    private var deviceMod: EasyDeviceMod? = null
//    private var networkMod: EasyNetworkMod? = null
//    private var simMod: EasySimMod? = null
//    private var appMod: EasyAppMod? = null
    private lateinit var speedometer: SpeedView

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentFirstBinding.inflate(inflater, container, false)
        return binding.root

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        speedometer = binding.speedView
        speedometer.setMinMaxSpeed(0F, 500F)
        speedometer.unit = " MB/Sec"
        speedometer.speedTextPosition = Gauge.Position.BOTTOM_CENTER
//        speedometer.speedTextColor = Color.WHITE
        speedometer.withTremble = false
//        speedometer.speedTo(45F)

        binding.textviewFirst.movementMethod = ScrollingMovementMethod()

//        binding.maxSpeed.text = measurementViewModel.maxBytesPerSec.value.toString()
        measurementViewModel.maxBytesPerSec.observe(viewLifecycleOwner) { newMaxBytesPerSec ->
            binding.maxSpeed.text = String.format("Max: %.2f MB/Sec", 8 * newMaxBytesPerSec / 1e6)
        }

        binding.buttonFirst.setOnClickListener {
            toggleButton(false)

            viewLifecycleOwner.lifecycleScope.launch {
                try {
//                    Ndt8MeasurementManager.runTestSequence()
                    measurementViewModel.runTestSequence()
                } catch (e: Exception) {
                    Log.e(TAG, "unexpected error running test sequence", e)
                    writeMessage("unexpected error running test sequence: ${e.localizedMessage}")
                } finally {
                    writeMessage("*** Done with Upload/Download Test ***")
                }

                toggleButton(true)
            }
        }

//        deviceMod = EasyDeviceMod(context)
//        networkMod = EasyNetworkMod(context)
//        simMod = EasySimMod(context)
//        appMod = EasyAppMod(context)

        viewLifecycleOwner.lifecycleScope.launch {
            measurementViewModel.bytesPerSecState.collect { bytesPerSec ->
                updateSpeedometer(8 * bytesPerSec / 1e6)
            }
        }
        Log.d(TAG, "***** FirstFragment Created *****")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    fun toggleButton(enabled: Boolean) {
        val handler = Handler(Looper.getMainLooper())
        handler.post {
//            binding.buttonFirst.setText(if (enabled) "Measure" else "Measuring")
            binding.buttonFirst.setText(if (enabled) R.string.measure else R.string.measuring)
            binding.buttonFirst.isEnabled = enabled
        }
    }

    fun writeMessage(m: String) {
        val handler = Handler(Looper.getMainLooper())
        handler.post {
            binding.textviewFirst.append("\n> $m")
        }
    }

    fun updateSpeedometer(megabytesPerSec: Double, moveDuration: Long = 200) {
        val handler = Handler(Looper.getMainLooper())

        Log.d(TAG, "megabytesPerSec = ${megabytesPerSec}")

        handler.post {
            speedometer.speedTo(megabytesPerSec.toFloat(), moveDuration)
        }
    }
}