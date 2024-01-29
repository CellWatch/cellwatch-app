package edu.gatech.cc.cellwatch.ui.measurement

import android.Manifest
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.method.ScrollingMovementMethod
import edu.gatech.cc.cellwatch.core.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import edu.gatech.cc.cellwatch.R
import edu.gatech.cc.cellwatch.data.model.Cell
import edu.gatech.cc.cellwatch.data.model.Location
import edu.gatech.cc.cellwatch.databinding.FragmentMeasurementBinding
import edu.gatech.cc.cellwatch.domain.telephony.managers.TelephonyInfoManager
import edu.gatech.cc.cellwatch.domain.fcc.LatencyResult
import edu.gatech.cc.cellwatch.domain.fcc.MeasurementManager
import edu.gatech.cc.cellwatch.domain.fcc.ThroughputResult
import edu.gatech.cc.cellwatch.ui.measurement.viewmodels.MeasurementViewModel
import edu.gatech.cc.cellwatch.ui.measurement.viewmodels.MeasurementViewModelFactory
import com.github.anastr.speedviewlib.SpeedView
import edu.gatech.cc.cellwatch.data.model.Measurement
import edu.gatech.cc.cellwatch.domain.fcc.ThroughputMetrics
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
        MeasurementViewModelFactory(edu.gatech.cc.cellwatch.CellWatchApp.measurementRepository)
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
        try {
            _binding = FragmentMeasurementBinding.inflate(inflater, container, false)
        } catch (e: Exception) {
            Log.e(TAG, "onCreateView exception:", e)
            throw e
        }
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
                        { handleLatencyComplete(it) },
                        { handleDownloadStart() },
                        { handleThroughputComplete(binding.downloadContent, binding.downloadDetails, it) },
                        { handleUploadStart() },
                        { handleThroughputComplete(binding.uploadContent, binding.uploadDetails, it) },
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

    fun handleLatencyComplete(m: Measurement) {
        binding.latencyContent.text = if (m.success == true) "success" else "failed"
        val mean = "Mean RTT: ${(m.latencyData?.rtt ?: 0) / 1e3}ms"
        val jitter = "Jitter: ${(m.latencyData?.jitter ?: 0) / 1e3}ms"
        val received = "Received: ${m.latencyData?.received}/${m.latencyData?.sent}"
        val start = "Start time: ${m.timestamp}"
        val duration = "Duration: ${(m.duration ?: 0) / 1e6}s"
        val target = "Target host: ${m.latencyData?.servers}"
        val startLoc = "Start location: ${locationToString(m.locations?.getOrNull(0))}"
        val endLoc = "End location: ${locationToString(m.locations?.getOrNull(1))}"
        val cells = "Cells: ${m.cells}"
        binding.latencyDetails.text = "$mean\n$jitter\n$received\n$start\n$duration\n$target\n$startLoc\n$endLoc\n$cells"
    }

    fun handleDownloadStart() {
        binding.downloadContent.text = "running..."
    }

    fun handleUploadStart() {
        binding.uploadContent.text = "running..."
    }

    fun handleThroughputComplete(content: TextView, details: TextView, m: Measurement) {
        content.text = if (m.success == true) "success" else "failed"
        val activeMetrics = ThroughputMetrics(m.uploadDownloadData?.bytes ?: 0, m.uploadDownloadData?.duration ?: 0)
        val speed = "Speed: ${(activeMetrics.bytesPerSec * 8 / 1e6).roundToInt()} Mbps"
        val start = "Start time: ${m.timestamp}"
        val duration = "Duration: ${(m.duration ?: 0) / 1e6}s"
        val target = "Target host: ${m.uploadDownloadData?.servers}"
        val startLoc = "Start location: ${locationToString(m.locations?.getOrNull(0))}"
        val endLoc = "End location: ${locationToString(m.locations?.getOrNull(1))}"
        val cells = "Cells: ${m.cells}"
        details.text = "$speed\n$start\n$duration\n$target\n$startLoc\n$endLoc\n$cells"
    }

    fun locationToString(l: Location?): String {
        if (l == null) {
            return ""
        }

        return String.format("lat=%f, lon=%f, speed=%.2fm/s", l.lat, l.lon, l.speed)
    }
}
