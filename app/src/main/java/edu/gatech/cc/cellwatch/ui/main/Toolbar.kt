package edu.gatech.cc.cellwatch.ui.main

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import edu.gatech.cc.cellwatch.databinding.ToolbarBinding

class Toolbar(
    context: Context,
    attrs: AttributeSet? = null,
) : FrameLayout(context, attrs) {
    private val binding: ToolbarBinding

    init {
        binding = ToolbarBinding.inflate(LayoutInflater.from(context), this, true)
    }

    fun setDrawerLayout(l: DrawerLayout) {
        binding.sideMenuButton.setOnClickListener {
            if (l.isDrawerOpen(GravityCompat.START)) {
                l.closeDrawer(GravityCompat.START)
            } else {
                l.openDrawer(GravityCompat.START)
            }
        }
    }
}