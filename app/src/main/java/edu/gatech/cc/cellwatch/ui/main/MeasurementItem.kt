package edu.gatech.cc.cellwatch.ui.main

import android.content.Context
import android.text.format.DateFormat
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import androidx.core.view.isVisible
import edu.gatech.cc.cellwatch.R
import edu.gatech.cc.cellwatch.core.util.setCopyOnClick
import edu.gatech.cc.cellwatch.data.model.Measurement
import edu.gatech.cc.cellwatch.databinding.ItemMeasurementBinding
import edu.gatech.cc.cellwatch.domain.fcc.ThroughputMetrics
import edu.gatech.cc.cellwatch.domain.telephony.managers.NetworkConnectionType
import edu.gatech.cc.cellwatch.domain.telephony.managers.TelephonyInfoManager
import kotlin.math.roundToInt

class MeasurementItem(
    context: Context,
    attrs: AttributeSet? = null,
): LinearLayout(context, attrs) {
    private val binding: ItemMeasurementBinding = ItemMeasurementBinding.inflate(
        LayoutInflater.from(context),
        this,
        true,
    )

    fun setData(type: String, measurement: Measurement?, notInHex: Boolean = false) {
        binding.headerIcon.setImageResource(when (type) {
            "latency" -> R.drawable.fa_stopwatch
            "download" -> R.drawable.fa_download
            "upload" -> R.drawable.fa_upload
            else -> throw RuntimeException("unknown type $type")
        })

        binding.headerLabel.setText(when (type) {
            "latency" -> R.string.latency
            "download" -> R.string.download
            "upload" -> R.string.upload
            else -> throw RuntimeException("unknown type $type")
        })

        binding.warningRow.isVisible = measurement == null || notInHex
        binding.timeRow.isVisible = measurement != null
        binding.locationRow.isVisible = measurement != null
        binding.technologyRow.isVisible = measurement != null
        binding.roamingRow.isVisible = measurement != null

        if (measurement == null) {
            binding.headerText.text = "-"
            binding.warningText.text = context.getString(R.string.not_measured)
            return
        }

        binding.warningText.text = context.getString(R.string.not_in_this_hexagon)

        binding.timeText.text = measurement.timestamp?.let {
            DateFormat.format("h:mm:ss a", it.toEpochMilliseconds())
        } ?: "-"

        binding.locationText.text = measurement.centerLatLon()?.let {
            val text = context.getString(R.string.latlon, it.first, it.second)
            binding.locationText.setCopyOnClick("location") { text }
            text
        } ?: "-"

        binding.headerText.text = if (measurement.success == true) {
            if (measurement.type == "latency") {
                measurement.latencyData?.let {
                    context.getString(R.string.latency_ms, ((it.rtt ?: 0) / 1e3).roundToInt())
                } ?: throw RuntimeException("missing latency data on measurement $measurement")
            } else {
                measurement.uploadDownloadData?.let {
                    val activeMetrics = ThroughputMetrics(it.bytes ?: 0, it.duration ?: 0)
                    context.getString(R.string.speed_mbps, (activeMetrics.bytesPerSec * 8 / 1e6).roundToInt())
                } ?: throw RuntimeException("missing upload/download data on measurement $measurement")
            }
        } else {
            context.getString(R.string.failed)
        }

        binding.technologyText.text = if (measurement.connectionType == NetworkConnectionType.WIFI) {
            context.getString(R.string.wifi)
        } else {
            measurement.cells?.let { TelephonyInfoManager.getDisplayGeneration(it) } ?: "-"
        }

        binding.roamingText.text = when (measurement.networkRoaming) {
            true -> context.getString(R.string.yes)
            false -> context.getString(R.string.no)
            null -> "-"
        }

    }
}