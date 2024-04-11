package edu.gatech.cc.cellwatch.ui.map

import android.content.Context
import android.text.format.DateFormat
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import edu.gatech.cc.cellwatch.R
import edu.gatech.cc.cellwatch.data.model.FccSubmission
import edu.gatech.cc.cellwatch.data.model.Measurement
import edu.gatech.cc.cellwatch.data.model.MeasurementGroup
import edu.gatech.cc.cellwatch.databinding.ItemMeasurementBinding
import edu.gatech.cc.cellwatch.domain.fcc.ThroughputMetrics
import edu.gatech.cc.cellwatch.domain.map.managers.H3Manager
import edu.gatech.cc.cellwatch.domain.telephony.managers.NetworkConnectionType
import edu.gatech.cc.cellwatch.domain.telephony.managers.TelephonyInfoManager
import kotlinx.datetime.Instant
import kotlin.math.roundToInt

class MeasurementItem(
    context: Context,
    attrs: AttributeSet? = null,
): LinearLayout(context, attrs) {
    private val binding: ItemMeasurementBinding
    private val expandable: Boolean

    init {
        binding = ItemMeasurementBinding.inflate(LayoutInflater.from(context), this, true)
        val arr = context.obtainStyledAttributes(attrs, R.styleable.MeasurementItem)
        expandable = arr.getBoolean(R.styleable.MeasurementItem_expandable, false)
        arr.recycle()
    }

    override fun onFinishInflate() {
        super.onFinishInflate()

        if (expandable) {
            fun setDetailsVisibility(visible: Boolean) {
                binding.metaContainer.visibility = if (visible) View.VISIBLE else View.GONE
                binding.resultsContainer.visibility = if (visible) View.VISIBLE else View.GONE
                binding.expandButton.rotation = if (visible) 90F else 0F
            }

            fun toggleDetails() {
                setDetailsVisibility(binding.metaContainer.visibility == View.GONE)
            }

            binding.expandButton.setOnClickListener { toggleDetails() }
            binding.itemHeader.setOnClickListener { toggleDetails() }
            setDetailsVisibility(false)
        } else {
            binding.expandButton.visibility = View.GONE
            binding.summary.visibility = View.GONE
        }
    }

    fun setMeasurementGroup(group: MeasurementGroup) {
        val timestamp = group.latency?.timestamp
            ?: group.download?.timestamp
            ?: group.upload?.timestamp

        if (timestamp != null) {
            binding.dateText.text =
                DateFormat.format("d MMM yyyy", timestamp.toEpochMilliseconds())
            binding.timeText.text =
                DateFormat.format("h:mm:ss a", timestamp.toEpochMilliseconds())
        } else {
            binding.dateText.text = "-"
            binding.timeText.text = "-"
        }

        val location = group.latency?.locations?.get(0)
            ?: group.download?.locations?.get(0)
            ?: group.upload?.locations?.get(0)

        val lat = location?.lat
        val lon = location?.lon
        binding.locationText.text = if (lat != null && lon != null) {
            context.getString(R.string.hex_index, H3Manager.getH3Index(lat, lon, 9))
        } else {
            "-"
        }

        val carrier = group.latency?.provider
            ?: group.download?.provider
            ?: group.upload?.provider

        binding.carrierText.text = carrier ?: ""

        val submission = group.submission
        binding.typeText.text = if (submission == null) {
            context.getString(R.string.testing)
        } else if (submission.inVehicle == true) {
            context.getString(R.string.fcc_challenge_vehicle)
        } else {
            context.getString(R.string.fcc_challenge_stationary)
        }

        val latencyResult = group.latency
        binding.latencyText.text = if (latencyResult == null) {
            "-"
        } else if (latencyResult.success == true) {
            context.getString(
                R.string.latency_ms,
                ((latencyResult.latencyData?.rtt ?: 0) / 1e3).roundToInt()
            )
        } else {
            context.getString(R.string.failed)
        }

        val downloadResult = group.download
        val downloadText = if (downloadResult == null) {
            "-"
        } else if (downloadResult.success == true) {
            context.getString(R.string.speed_mbps, getSpeedMbps(downloadResult))
        } else {
            context.getString(R.string.failed)
        }
        binding.summaryDownloadText.text = downloadText
        binding.downloadText.text = downloadText

        val uploadResult = group.upload
        val uploadText = if (uploadResult == null) {
            "-"
        } else if (uploadResult.success == true) {
            context.getString(R.string.speed_mbps, getSpeedMbps(uploadResult))
        } else {
            context.getString(R.string.failed)
        }
        binding.summaryUploadText.text = uploadText
        binding.uploadText.text = uploadText

        val result = group.latency ?: group.download ?: group.upload
        binding.technologyText.text = if (result?.connectionType == NetworkConnectionType.WIFI) {
            context.getString(R.string.wifi)
        } else {
            TelephonyInfoManager.getDisplayGeneration(result?.cells ?: listOf())
        }

        val roaming = group.latency?.networkRoaming
            ?: group.download?.networkRoaming
            ?: group.upload?.networkRoaming

        binding.roamingText.text = when (roaming) {
            true -> context.getString(R.string.yes)
            false -> context.getString(R.string.no)
            null -> ""
        }

        val uploadable = listOfNotNull(group.latency, group.download, group.upload, group.submission)
        val uploadTimes = uploadable.map {
            when (it) {
                is Measurement -> it.uploadTime
                is FccSubmission -> it.uploadTime
                else -> throw RuntimeException("expected Measurement or FccSubmission, got $it")
            }
        }
        val uploadTime = if (uploadTimes.all { it != null }) uploadTimes.firstOrNull() else null
        updateUploadTime(uploadTime)
    }

    fun updateUploadTime(uploadTime: Instant?) {
        binding.uploadedText.text = if (uploadTime == null) {
            context.getString(R.string.pending)
        } else {
            DateFormat.format("d MMM yyyy h:mm:ss a", uploadTime.toEpochMilliseconds())
        }
    }

    private fun getSpeedMbps(m: Measurement): Int {
        val activeMetrics = ThroughputMetrics(m.uploadDownloadData?.bytes ?: 0, m.uploadDownloadData?.duration ?: 0)
        return (activeMetrics.bytesPerSec * 8 / 1e6).roundToInt()
    }
}