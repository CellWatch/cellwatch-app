package edu.gatech.cc.cellwatch.ui.main

import android.content.Context
import android.graphics.Color
import android.text.format.DateFormat
import android.util.AttributeSet
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.view.updateMargins
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.findViewTreeViewModelStoreOwner
import com.google.android.flexbox.FlexboxLayout
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
    private val summaryLabels: Boolean
    private var currentGroup: MeasurementGroup? = null
    private var model: MeasurementGroupItemViewModel? = null

    init {
        val arr = context.obtainStyledAttributes(attrs, R.styleable.MeasurementGroupItem)
        expandable = arr.getBoolean(R.styleable.MeasurementGroupItem_expandable, false)
        summaryLabels = arr.getBoolean(R.styleable.MeasurementGroupItem_summaryLabels, false)
        arr.recycle()
    }

    override fun onFinishInflate() {
        super.onFinishInflate()

        val dp = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 1F, resources.displayMetrics).toInt()
        for (s in listOf(binding.summaryLatency, binding.summaryDownload, binding.summaryUpload)) {
            val params = FlexboxLayout.LayoutParams(
                if (summaryLabels) LayoutParams.MATCH_PARENT else LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT,
            )

            if (summaryLabels) {
                params.updateMargins(top = 4 * dp, bottom = 4 * dp)
            } else if (s !== binding.summaryUpload) {
                params.marginEnd = 8 * dp
            }

            s.layoutParams = params
        }

        binding.summaryLatencyLabel.isVisible = summaryLabels
        binding.summaryDownloadLabel.isVisible = summaryLabels
        binding.summaryUploadLabel.isVisible = summaryLabels

        if (expandable) {
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

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        model = this.findViewTreeViewModelStoreOwner()?.let {
            ViewModelProvider(it)[MeasurementGroupItemViewModel::class.java]
        }
    }

    private fun setDetailsVisibility(visible: Boolean) {
        currentGroup?.id?.let {
            if (visible) {
                model?.expandedGroupIds?.add(it)
            } else {
                model?.expandedGroupIds?.remove(it)
            }
        }

        binding.metaContainer.isVisible = visible
        binding.resultsContainer.isVisible = visible
        binding.summary.isVisible = !visible
        binding.expandButton.rotation = if (visible) 90F else 0F
    }

    fun setData(
        group: MeasurementGroup,
        collectionMode: CollectionMode? = null,
        inVehicle: Boolean? = null,
        displayedHexAddress: Long? = null,
        inProgress: Boolean = false,
    ) {
        currentGroup = group
        if (expandable) {
            model?.let { setDetailsVisibility(group.id in it.expandedGroupIds) }
        }

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

        binding.summaryLatencyText.text = group.latency?.displayValue(context) ?: "-"
        binding.summaryDownloadText.text = group.download?.displayValue(context) ?: "-"
        binding.summaryUploadText.text = group.upload?.displayValue(context) ?: "-"

        binding.latencyItem.setData("latency", group.latency, displayedHexAddress, inProgress)
        binding.downloadItem.setData("download", group.download, displayedHexAddress, inProgress)
        binding.uploadItem.setData("upload", group.upload, displayedHexAddress, inProgress)
    }

    fun updateUploadTime(uploadTime: Instant?) {
        binding.uploadedText.text = if (uploadTime == null) {
            context.getString(R.string.pending)
        } else {
            DateFormat.format("d MMM yyyy h:mm:ss a", uploadTime.toEpochMilliseconds())
        }
    }

    fun setItemBackground(backgroundResId: Int) {
        val drawable = ContextCompat.getDrawable(context, backgroundResId)
        binding.item.background = drawable
    }

    fun setIcon(iconResId: Int) {
        binding.measureTypeIcon.setImageResource(iconResId)
        binding.measureTypeIcon.isVisible = true
    }

    fun setMeasurementTypeText(text: String) {
        binding.measureTypeText.text = text
        binding.measureTypeText.isVisible = true
    }
}