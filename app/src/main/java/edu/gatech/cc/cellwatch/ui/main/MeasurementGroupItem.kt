package edu.gatech.cc.cellwatch.ui.main

import android.content.Context
import android.text.format.DateFormat
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import androidx.core.view.isVisible
import edu.gatech.cc.cellwatch.R
import edu.gatech.cc.cellwatch.data.model.CollectionMode
import edu.gatech.cc.cellwatch.data.model.FccSubmission
import edu.gatech.cc.cellwatch.data.model.Measurement
import edu.gatech.cc.cellwatch.data.model.MeasurementGroup
import edu.gatech.cc.cellwatch.databinding.ItemMeasurementGroupBinding
import kotlinx.datetime.Instant

class MeasurementGroupItem(
    context: Context,
    attrs: AttributeSet? = null,
): LinearLayout(context, attrs) {
    private val binding: ItemMeasurementGroupBinding = ItemMeasurementGroupBinding.inflate(
        LayoutInflater.from(context),
        this,
        true,
    )
    private val expandable: Boolean

    init {
        val arr = context.obtainStyledAttributes(attrs, R.styleable.MeasurementGroupItem)
        expandable = arr.getBoolean(R.styleable.MeasurementGroupItem_expandable, false)
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
        displayedHexAddress: Long? = null,
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

        binding.latencyItem.setData("latency", group.latency, displayedHexAddress)
        binding.downloadItem.setData("download", group.download, displayedHexAddress)
        binding.uploadItem.setData("upload", group.upload, displayedHexAddress)
    }

    fun updateUploadTime(uploadTime: Instant?) {
        binding.uploadedText.text = if (uploadTime == null) {
            context.getString(R.string.pending)
        } else {
            DateFormat.format("d MMM yyyy h:mm:ss a", uploadTime.toEpochMilliseconds())
        }
    }
}