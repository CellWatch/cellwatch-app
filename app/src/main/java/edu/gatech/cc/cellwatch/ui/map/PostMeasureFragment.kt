package edu.gatech.cc.cellwatch.ui.map

import android.os.Bundle
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import edu.gatech.cc.cellwatch.R
import edu.gatech.cc.cellwatch.data.model.Measurement
import edu.gatech.cc.cellwatch.databinding.FragmentPostMeasureBinding
import edu.gatech.cc.cellwatch.domain.fcc.ThroughputMetrics
import edu.gatech.cc.cellwatch.domain.map.managers.H3Manager
import edu.gatech.cc.cellwatch.domain.telephony.managers.NetworkConnectionType
import edu.gatech.cc.cellwatch.domain.telephony.managers.TelephonyInfoManager
import kotlin.math.roundToInt

class PostMeasureFragment: Fragment() {
    private lateinit var binding: FragmentPostMeasureBinding
    private lateinit var model: MeasurementViewModel

    interface PostMeasureFragmentInteractionListener {
        fun onTakeAnotherMeasurementPressed()
        fun onBackToMapPressed()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentPostMeasureBinding.inflate(inflater, container, false)
        model = ViewModelProvider(requireActivity())[MeasurementViewModel::class.java]

        displayDateTime()
        displayLocation()
        displayCarrier()
        displayType()
        displayLatency()
        displayDownload()
        displayUpload()
        displayTechnology()
        displayRoaming()
        displayUploaded()

        val interactionListener = if (context is PostMeasureFragmentInteractionListener) {
            context as PostMeasureFragmentInteractionListener
        } else {
            throw RuntimeException(context.toString() + " must implement PostMeasureFragmentInteractionListener")
        }

        binding.takeAnotherButton.setOnClickListener { interactionListener.onTakeAnotherMeasurementPressed() }
        binding.backToMapButton.setOnClickListener { interactionListener.onBackToMapPressed() }

        return binding.root
    }

    private fun displayDateTime() {
        val timestamp = model.latencyResult?.timestamp
            ?: model.downloadResult?.timestamp
            ?: model.uploadResult?.timestamp

        if (timestamp != null) {
            binding.dateText.text = DateFormat.format("d MMM yyyy", timestamp.toEpochMilliseconds())
            binding.timeText.text = DateFormat.format("h:mm:ss a", timestamp.toEpochMilliseconds())
        } else {
            binding.dateText.text = ""
            binding.timeText.text = ""
        }
    }

    private fun displayLocation() {
        val location = model.latencyResult?.locations?.get(0)
            ?: model.downloadResult?.locations?.get(0)
            ?: model.uploadResult?.locations?.get(0)

        val lat = location?.lat
        val lon = location?.lon
        binding.locationText.text = if (lat != null && lon != null) {
            getString(R.string.cell_index, H3Manager.getH3Index(lat, lon, 9))
        } else {
            ""
        }
    }

    private fun displayCarrier() {
        val carrier = model.latencyResult?.provider
            ?: model.downloadResult?.provider
            ?: model.uploadResult?.provider

        binding.carrierText.text = carrier ?: ""
    }

    private fun displayType() {
        binding.typeText.text = if (model.inVehicle) {
            getString(R.string.moving_vehicle)
        } else {
            getString(R.string.stationary_outdoors)
        }
    }

    private fun displayLatency() {
        val latencyResult = model.latencyResult
        binding.latencyText.text = if (latencyResult?.success == true) {
            getString(R.string.latency_ms, ((latencyResult.latencyData?.rtt ?: 0) / 1e3).roundToInt())
        } else {
            getString(R.string.failed)
        }
    }

    private fun displayDownload() {
        val downloadResult = model.downloadResult
        binding.downloadText.text = if (downloadResult?.success == true) {
            getString(R.string.speed_mbps, getSpeedMbps(downloadResult))
        } else {
            getString(R.string.failed)
        }
    }

    private fun displayUpload() {
        val uploadResult = model.uploadResult
        binding.uploadText.text = if (uploadResult?.success == true) {
            getString(R.string.speed_mbps, getSpeedMbps(uploadResult))
        } else {
            getString(R.string.failed)
        }
    }

    private fun displayTechnology() {
        val result = model.latencyResult ?: model.downloadResult ?: model.uploadResult
        if (result?.connectionType == NetworkConnectionType.WIFI) {
            binding.technologyText.text = getString(R.string.wifi)
            return
        }

        binding.technologyText.text = TelephonyInfoManager.getDisplayGeneration(result?.cells ?: listOf())
    }

    private fun displayRoaming() {
        val roaming = model.latencyResult?.networkRoaming
            ?: model.downloadResult?.networkRoaming
            ?: model.uploadResult?.networkRoaming

        binding.roamingText.text = when (roaming) {
            true -> getString(R.string.yes)
            false -> getString(R.string.no)
            null -> ""
        }
    }

    private fun displayUploaded() {
        // TODO: display upload time
        binding.uploadedText.text = getString(R.string.pending)
    }

    private fun getSpeedMbps(m: Measurement): Int {
        val activeMetrics = ThroughputMetrics(m.uploadDownloadData?.bytes ?: 0, m.uploadDownloadData?.duration ?: 0)
        return (activeMetrics.bytesPerSec * 8 / 1e6).roundToInt()
    }
}