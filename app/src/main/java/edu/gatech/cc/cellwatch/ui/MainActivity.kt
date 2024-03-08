package edu.gatech.cc.cellwatch.ui

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import edu.gatech.cc.cellwatch.R
import edu.gatech.cc.cellwatch.databinding.ActivityMainBinding
import edu.gatech.cc.cellwatch.ui.map.MapFragment
import edu.gatech.cc.cellwatch.ui.map.MeasureFragment
import edu.gatech.cc.cellwatch.ui.map.MeasureHistoryFragment
import edu.gatech.cc.cellwatch.ui.map.PostMeasureFragment
import edu.gatech.cc.cellwatch.ui.map.PreMeasureFragment
import edu.gatech.cc.cellwatch.ui.map.SettingsFragment

class MainActivity : AppCompatActivity(),
    MapFragment.DrawerToggleListener,
    MapFragment.OnMapFragmentInteractionListener,
    PreMeasureFragment.PreMeasureFragmentInteractionListener,
    MeasureFragment.MeasureFragmentInteractionListener,
    PostMeasureFragment.PostMeasureFragmentInteractionListener {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        createDrawerLayout()
        replaceFragment(MapFragment())
    }


    private fun createDrawerLayout() {
        // Hamburger Button functionality
        binding.sideMenuButton.setOnClickListener {
            if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                binding.drawerLayout.closeDrawer(GravityCompat.START)
            } else {
                binding.drawerLayout.openDrawer(GravityCompat.START)
            }
        }

        // Exit button functionality
        binding.navDrawer.menuCloseButton.setOnClickListener {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        }

        // Map button functionality
        binding.navDrawer.mapButton.setOnClickListener {
            replaceFragment(MapFragment())
        }

        // Measure button functionality
        binding.navDrawer.menuMeasureButton.setOnClickListener {
            replaceFragment(PreMeasureFragment())
        }

        // Measurement History button functionality
        binding.navDrawer.historyButton.setOnClickListener {
            replaceFragment(MeasureHistoryFragment())
        }

        // Settings button functionality
        binding.navDrawer.settingsButton.setOnClickListener {
            replaceFragment(SettingsFragment())
        }

        // Help button functionality
        binding.navDrawer.helpButton.setOnClickListener {
            //TODO redirect to a web page
        }
    }

    private fun replaceFragment(fragment: Fragment) {
        if (fragment is MapFragment || fragment is MeasureFragment) {
            binding.toolbar.visibility = View.GONE
        } else {
            binding.toolbar.visibility = View.VISIBLE
        }

        listOf(
            binding.navDrawer.mapButton,
            binding.navDrawer.menuMeasureButton,
            binding.navDrawer.historyButton,
            binding.navDrawer.settingsButton,
            binding.navDrawer.helpButton,
        ).forEach { it.setBackgroundColor(getColor(R.color.cw_blue)) }

        val activeButton = when (fragment) {
            is MapFragment -> binding.navDrawer.mapButton
            is MeasureFragment, is PreMeasureFragment, is PostMeasureFragment -> binding.navDrawer.menuMeasureButton
            is MeasureHistoryFragment -> binding.navDrawer.historyButton
            is SettingsFragment -> binding.navDrawer.settingsButton
            else -> null
        }

        activeButton?.setBackgroundColor(getColor(R.color.cw_blue_light))

        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
        binding.drawerLayout.closeDrawer(GravityCompat.START)
    }

    override fun toggleDrawer() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }
    }

    override fun onMeasureButtonPressed() {
        replaceFragment(PreMeasureFragment())
    }

    override fun onGoButtonPressed() {
        replaceFragment(MeasureFragment())
    }

    override fun onMeasurementComplete() {
        replaceFragment(PostMeasureFragment())
    }

    override fun onTakeAnotherMeasurementPressed() {
        replaceFragment(PreMeasureFragment())
    }

    override fun onBackToMapPressed() {
        replaceFragment(MapFragment())
    }
}