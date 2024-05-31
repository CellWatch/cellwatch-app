package edu.gatech.cc.cellwatch.ui.main

import android.content.Context
import android.text.format.DateFormat
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import androidx.core.view.isVisible
import edu.gatech.cc.cellwatch.R
import edu.gatech.cc.cellwatch.core.util.setCopyOnClick
import edu.gatech.cc.cellwatch.data.model.CollectionMode
import edu.gatech.cc.cellwatch.data.model.FccSubmission
import edu.gatech.cc.cellwatch.data.model.Measurement
import edu.gatech.cc.cellwatch.data.model.MeasurementGroup
import edu.gatech.cc.cellwatch.databinding.ItemMeasurementBinding
import edu.gatech.cc.cellwatch.domain.fcc.ThroughputMetrics
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
                binding.metaContainer.isVisible = visible
                binding.resultsContainer.isVisible = visible
                binding.sep1.isVisible = visible
                binding.sep2.isVisible = visible
                binding.summary.isVisible = !visible
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

    fun setData(
        group: MeasurementGroup,
        collectionMode: CollectionMode? = null,
        inVehicle: Boolean? = null,
    ) {
        val timestamp = group.latency?.timestamp
            ?: group.download?.timestamp
            ?: group.upload?.timestamp

        if (timestamp != null) {
            binding.dateText.text =
                DateFormat.format("d MMM yyyy", timestamp.toEpochMilliseconds())
            binding.timeText.text =
                DateFormat.format("h:mm:ss a", timestamp.toEpochMilliseconds())
        } else {
            binding.dateText.text = ""
            binding.timeText.text = "-"
        }

        val latlon = group.centerLatLon()
        if (latlon != null) {
            binding.locationText.text = context.getString(R.string.latlon, latlon.first, latlon.second)
            binding.locationText.setCopyOnClick("location") { binding.locationText.text }
        } else {
            binding.locationText.text = "-"
        }

        val carrier = group.latency?.provider
            ?: group.download?.provider
            ?: group.upload?.provider

        binding.carrierText.text = carrier ?: ""

        val submission = group.submission
        binding.typeText.text = if (submission == null && collectionMode != CollectionMode.FCC_CHALLENGE) {
            context.getString(R.string.testing)
        } else if (submission?.inVehicle == true || inVehicle == true) {
            context.getString(R.string.fcc_challenge_vehicle)
        } else {
            context.getString(R.string.fcc_challenge_stationary)
        }

        val latencyResult = group.latency
        val latencyText = if (latencyResult == null) {
            "-"
        } else if (latencyResult.success == true) {
            context.getString(
                R.string.latency_ms,
                ((latencyResult.latencyData?.rtt ?: 0) / 1e3).roundToInt()
            )
        } else {
            context.getString(R.string.failed)
        }
        binding.summaryLatencyText.text = latencyText
        binding.latencyText.text = latencyText

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