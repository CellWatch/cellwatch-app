package edu.gatech.cc.cellwatch.ui

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.Toolbar
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import edu.gatech.cc.cellwatch.R
import edu.gatech.cc.cellwatch.ui.map.*
import edu.gatech.cc.cellwatch.ui.map.MapFragment
import edu.gatech.cc.cellwatch.ui.map.MeasureFragment
import edu.gatech.cc.cellwatch.ui.map.MeasureHistoryFragment
import edu.gatech.cc.cellwatch.ui.map.SettingsFragment

class MainActivity : AppCompatActivity(), MapFragment.DrawerToggleListener {
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var toolbar: androidx.appcompat.widget.Toolbar
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        toolbar = findViewById(R.id.toolbar)
        createDrawerLayout();

        replaceFragment(MapFragment())
    }


    private fun createDrawerLayout() {
        drawerLayout = findViewById(R.id.drawer_layout)

        // Hamburger Button functionality
        val hamburgerButton: ImageButton = findViewById(R.id.sideMenuButton)
        hamburgerButton.setOnClickListener {
            if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.closeDrawer(GravityCompat.START)
            } else {
                drawerLayout.openDrawer(GravityCompat.START)
            }
        }

        // Exit button functionality
        val exitButton: ImageButton = findViewById(R.id.menuCloseButton)
        exitButton.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
        }

        // Map button functionality
        val mapButton: Button = findViewById(R.id.mapButton)
        mapButton.setOnClickListener {
            replaceFragment(MapFragment())
        }

        // Measure button functionality
        val measureButton: Button = findViewById(R.id.menuMeasureButton)
        measureButton.setOnClickListener {
            replaceFragment(MeasureFragment())
        }

        // Measurement History button functionality
        val historyButton: Button = findViewById(R.id.historyButton)
        historyButton.setOnClickListener {
            replaceFragment(MeasureHistoryFragment())
        }

        // Settings button functionality
        val settingsButton: Button = findViewById(R.id.settingsButton)
        settingsButton.setOnClickListener {
            replaceFragment(SettingsFragment())
        }

        // Help button functionality
        val helpButton: Button = findViewById(R.id.helpButton)
        helpButton.setOnClickListener {
            //TODO redirect to a web page
        }
    }

    private fun replaceFragment(fragment: Fragment) {
        if (fragment is MapFragment) {
            toolbar.visibility = View.GONE
        } else {
            toolbar.visibility = View.VISIBLE
        }

        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
        drawerLayout.closeDrawer(GravityCompat.START)
    }

    override fun toggleDrawer() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            drawerLayout.openDrawer(GravityCompat.START)
        }
    }
}