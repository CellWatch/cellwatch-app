package com.cellwatch.ui.measurement

import android.Manifest
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.cellwatch.R
import com.cellwatch.data.model.Cell
import com.cellwatch.data.model.Location
import com.cellwatch.databinding.FragmentMeasurementBinding
import com.cellwatch.domain.telephony.managers.TelephonyInfoManager
import com.cellwatch.domain.fcc.LatencyResult
import com.cellwatch.domain.fcc.MeasurementManager
import com.cellwatch.domain.fcc.ThroughputResult
import com.cellwatch.ui.measurement.viewmodels.MeasurementViewModel
import com.cellwatch.ui.measurement.viewmodels.MeasurementViewModelFactory
import com.github.anastr.speedviewlib.SpeedView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class MeasurementFragment : Fragment() {
    private val TAG = this::class.simpleName
    private var _binding: FragmentMeasurementBinding? = null

    private val measurementViewModel: MeasurementViewModel by activityViewModels() {
        MeasurementViewModelFactory(com.cellwatch.CellWatchApp.measurementRepository)
    }

    private val standardPermissions = arrayOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.READ_PHONE_STATE
    )

    private val standardPermissionRequest = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permission ->
        if (permission[standardPermissions[0]] == true && permission[standardPermissions[1]] == true && permission[standardPermissions[2]] == true) {
            // permissions granted
            Log.d(TAG, "Permissions are granted!")
        } else {
            Log.d(TAG, "Permissions are not granted!!!")
        }
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
        _binding = FragmentMeasurementBinding.inflate(inflater, container, false)
        return binding.root

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        standardPermissionRequest.launch(standardPermissions)
        binding.latencyDetails.movementMethod = ScrollingMovementMethod()
        binding.downloadDetails.movementMethod = ScrollingMovementMethod()
        binding.uploadDetails.movementMethod = ScrollingMovementMethod()


//        speedometer = binding.speedView
//        speedometer.setMinMaxSpeed(0F, 10F)
//        speedometer.unit = " MB/Sec"
//        speedometer.speedTextPosition = Gauge.Position.BOTTOM_CENTER
//        speedometer.speedTextColor = Color.WHITE
//        speedometer.withTremble = false
//        speedometer.speedTo(45F)

        binding.buttonMeasure.setOnClickListener {
//            startMeasuring()
//            val connectionType = TelephonyInfoManager.getConnectionType()
//            run {
//                Toast.makeText(context, "Connection type is ${connectionType.toString()}",
//                    Toast.LENGTH_SHORT).show()
//            }
            toggleButton(false)

            viewLifecycleOwner.lifecycleScope.launch {
                binding.locateStatus.text = ""
                binding.latencyContent.text = ""
                binding.downloadContent.text = ""
                binding.uploadContent.text = ""
                binding.latencyDetails.text = ""
                binding.downloadDetails.text = ""
                binding.uploadDetails.text = ""

                try {
//                    measurementViewModel.runTestSequence()
                    MeasurementManager.runTestSequence(
                        { binding.locateStatus.text = "finding server..." },
                        {r -> binding.locateStatus.text = "found server $r"},
                        { handleLatencyStart() },
                        {r, l, c -> handleLatencyComplete(r, l, c)},
                        { handleDownloadStart() },
                        {r, l, c -> handleThroughputComplete(binding.downloadContent, binding.downloadDetails, r, l, c)},
                        { handleUploadStart() },
                        {r, l, c -> handleThroughputComplete(binding.uploadContent, binding.uploadDetails, r, l, c)},
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "unexpected error running test sequence", e)
                    writeMessage("unexpected error running test sequence: ${e.localizedMessage}")

                    if (binding.locateStatus.text == "" || binding.locateStatus.text == "finding server...") {
                        binding.locateStatus.text == "failed to find server"
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
                } finally {
                    writeMessage("*** Done with Upload/Download Test ***")
                }

//                binding.maxSpeed.text = String.format("Max: 0.0 MB/Sec")

                toggleButton(true)
            }
        }

//        deviceMod = EasyDeviceMod(context)
//        networkMod = EasyNetworkMod(context)
//        simMod = EasySimMod(context)
//        appMod = EasyAppMod(context)

        viewLifecycleOwner.lifecycleScope.launch {
            var bytesPerSec: Double = 0.0
            var bytesPerSecList = mutableListOf<Double>()
            var maxBytesPerSec: Double = 0.0

            launch {
                while(true) {
                    val avgBytesPerSec = if (bytesPerSecList.isEmpty()) 0.0 else bytesPerSecList.average()
                    bytesPerSecList.clear()
//                    val currentMaxBytesPerSec = bytesPerSecList.maxOrNull() ?: 0.0
                    maxBytesPerSec =  if (maxBytesPerSec > avgBytesPerSec) maxBytesPerSec else avgBytesPerSec
//                    updateSpeedometerRange(8 * maxBytesPerSec / 1e6)
//                    updateSpeedometer(8 * bytesPerSec / 1e6)
                    delay(20)
                }
            }

//            measurementViewModel.bytesPerSecState.collect {
//                bytesPerSec = it
//                bytesPerSecList.add(it)
//                updateSpeedometer(8 * bytesPerSec / 1e6)
//            }
//            measurementViewModel.bytesPerSecState.collect { bytesPerSec ->
//                updateSpeedometer(8 * bytesPerSec / 1e6)
//            }
        }
        Log.d(TAG, "***** MeasurementFragment Created *****")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

//    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
//        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
//
//        // EasyPermissions handles the request result.
//        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this)
//    }

    // Measuring methods
    private fun startMeasuring() {
        val connectionType = TelephonyInfoManager.getConnectionType()
        run {
            Toast.makeText(context, "Connection type is ${connectionType.toString()}",
                Toast.LENGTH_SHORT).show()
        }
        toggleButton(false)

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                measurementViewModel.runTestSequence()
            } catch (e: Exception) {
                Log.e(TAG, "unexpected error running test sequence", e)
                writeMessage("unexpected error running test sequence: ${e.localizedMessage}")
            } finally {
                writeMessage("*** Done with Upload/Download Test ***")
            }

//                binding.maxSpeed.text = String.format("Max: 0.0 MB/Sec")

            toggleButton(true)
        }
    }

    fun toggleButton(enabled: Boolean) {
        val handler = Handler(Looper.getMainLooper())
        handler.post {
//            binding.buttonMeasure.setText(if (enabled) "Measure" else "Measuring")
            binding.buttonMeasure.setText(if (enabled) R.string.measure else R.string.measuring)
            binding.buttonMeasure.isEnabled = enabled
        }
    }

    fun writeMessage(m: String) {
        val handler = Handler(Looper.getMainLooper())
        handler.post {
            //binding.textviewFirst.append("\n> $m")
        }
    }

//    fun updateSpeedometer(megabytesPerSec: Double, moveDuration: Long = 1000) {
//        val handler = Handler(Looper.getMainLooper())
//
//        Log.d(TAG, "megabytesPerSec = ${megabytesPerSec}")
//
//        handler.post {
//            speedometer.speedTo(megabytesPerSec.toFloat(), moveDuration)
//        }
//    }
//
//    fun updateSpeedometerRange(maxMegabytesPerSec: Double) {
//        if (maxMegabytesPerSec == 0.0) return
//
//        val handler = Handler(Looper.getMainLooper())
//
//        Log.d(TAG, "maxMegabytesPerSec = ${maxMegabytesPerSec}")
//
//        handler.post {
//            speedometer.setMinMaxSpeed(0F, maxMegabytesPerSec.toFloat() * 1.2F)
//        }
//    }

    fun handleLatencyStart() {
        binding.latencyContent.text = "running..."
    }

    fun handleLatencyComplete(r: LatencyResult, l: List<Location>, c: List<Cell>) {
        if (!r.success) {
            binding.latencyContent.text = "failed"
            return
        }

        binding.latencyContent.text = "success"
        val mean = "Mean RTT: ${r.meanRtt / 1e3}ms"
        val jitter = "Jitter: ${r.jitter / 1e3}ms"
        val received = "Received: ${r.packetsReceived}/${r.packetsSent}"
        val start = "Start time: ${r.start}"
        val duration = "Duration: ${r.usecs / 1e6}s"
        val target = "Target host: ${r.targetHost}"
        val startLoc = "Start location: ${locationToString(l.getOrNull(0))}"
        val endLoc = "End location: ${locationToString(l.getOrNull(1))}"
        val cells = "Cells: ${c}"
        binding.latencyDetails.text = "$mean\n$jitter\n$received\n$start\n$duration\n$target\n$startLoc\n$endLoc\n$cells"
    }

    fun handleDownloadStart() {
        binding.downloadContent.text = "running..."
    }

    fun handleUploadStart() {
        binding.uploadContent.text = "running..."
    }

    fun handleThroughputComplete(content: TextView, details: TextView, r: ThroughputResult, l: List<Location>, c: List<Cell>) {
        if (!r.success || r.activeMetrics == null) {
            content.text = "failed"
            return
        }

        content.text = "success"
        val speed = "Speed: ${(r.activeMetrics.bytesPerSec * 8 / 1e6).roundToInt()} Mbps"
        val start = "Start time: ${r.start}"
        val duration = "Duration: ${(r.activeMetrics.usecs + (r.warmupMetrics?.usecs ?: 0)) / 1e6}s"
        val target = "Target host: ${r.targetHost}"
        val startLoc = "Start location: ${locationToString(l.getOrNull(0))}"
        val endLoc = "End location: ${locationToString(l.getOrNull(1))}"
        val cells = "Cells: ${c}"
        details.text = "$speed\n$start\n$duration\n$target\n$startLoc\n$endLoc\n$cells"
    }

    fun locationToString(l: Location?): String {
        if (l == null) {
            return ""
        }

        return String.format("lat=%f, lon=%f, speed=%.2fm/s", l.lat, l.lon, l.speed)
    }
}