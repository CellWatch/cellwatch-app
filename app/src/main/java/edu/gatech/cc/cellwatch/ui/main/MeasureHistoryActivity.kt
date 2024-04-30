package edu.gatech.cc.cellwatch.ui.main

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import edu.gatech.cc.cellwatch.CellWatchApp
import edu.gatech.cc.cellwatch.databinding.ActivityMeasureHistoryBinding
import edu.gatech.cc.cellwatch.ui.main.MeasurementAdapter

class MeasureHistoryActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMeasureHistoryBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMeasureHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.navDrawer.setOnCloseListener { binding.root.closeDrawer(GravityCompat.START) }
        binding.navDrawer.setActiveActivity(this)
        binding.toolbar.setDrawerLayout(binding.root)

        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = MeasurementAdapter(this) {
            CellWatchApp.measurementRepository.getMeasurementGroups()
        }
    }
}