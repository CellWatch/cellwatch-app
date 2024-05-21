package edu.gatech.cc.cellwatch.ui.main

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.view.LayoutInflater
import com.google.android.material.navigation.NavigationView
import edu.gatech.cc.cellwatch.R
import edu.gatech.cc.cellwatch.databinding.NavigationDrawerLayoutBinding

class NavigationDrawer(
    context: Context,
    attrs: AttributeSet? = null,
): NavigationView(context, attrs) {
    private val binding: NavigationDrawerLayoutBinding
    private var closeListener: () -> Unit = {}
    private var activeActivity: Activity? = null

    init {
        binding = NavigationDrawerLayoutBinding.inflate(LayoutInflater.from(context), this, true)
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        binding.menuCloseButton.setOnClickListener { closeListener() }
        binding.mapButton.setOnClickListener { switchTo(MapActivity::class.java) }
        binding.menuMeasureButton.setOnClickListener { switchTo(MeasureActivity::class.java) }
        binding.historyButton.setOnClickListener { switchTo(MeasureHistoryActivity::class.java) }
        binding.settingsButton.setOnClickListener { switchTo(SettingsActivity::class.java) }
        binding.helpButton.setOnClickListener {switchTo(AboutActivity::class.java)}
    }

    fun setOnCloseListener(fn: () -> Unit) {
        closeListener = fn
    }

    fun setActiveActivity(a: Activity) {
        activeActivity = a
        val activeButton = when (activeActivity) {
            is MapActivity -> binding.mapButton
            is MeasureActivity -> binding.menuMeasureButton
            is MeasureHistoryActivity -> binding.historyButton
            is SettingsActivity -> binding.settingsButton
            else -> null
        }

        activeButton?.setBackgroundColor(context.getColor(R.color.cw_blue_light))
    }

    private fun <T>switchTo(activity: Class<T>) {
        closeListener()

        val aa = activeActivity
        if (aa == null || activity != aa::class.java) {
            context.startActivity(Intent(context, activity))
        }
    }
}