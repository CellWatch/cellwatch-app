package com.cellwatch.ui

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.cellwatch.R
import com.mapbox.maps.MapView
import com.mapbox.maps.Style

var mapView: MapView? = null

class MapActivity : AppCompatActivity() {
    private val TAG = "MapActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)

        val drawerLayout = findViewById<DrawerLayout>(R.id.drawerLayout)
        val hamburgerButton = findViewById<ImageButton>(R.id.sideMenuButton)
        val exitButton = findViewById<ImageButton>(R.id.menuCloseButton)
        val h3ToggleSwitch = findViewById<SwitchCompat>(R.id.h3ToggleSwitch)
        val measureButton = findViewById<Button>(R.id.measureButton)

        hamburgerButton.setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        exitButton.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
        }

        h3ToggleSwitch.setOnCheckedChangeListener { _, isChecked ->
            // Check the isChecked boolean to see if the switch is on or off
            if(isChecked) {
                Toast.makeText(this@MapActivity, "H3 overlay is ON", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@MapActivity, "H3 overlay is OFF", Toast.LENGTH_SHORT).show()
            }
        }

        measureButton.setOnClickListener {
            Toast.makeText(this@MapActivity, "Measure taken", Toast.LENGTH_SHORT).show()
        }

        mapView = findViewById(R.id.mapView)
        mapView?.getMapboxMap()?.loadStyleUri(Style.LIGHT)
        Log.i(TAG, "Instantiated Map")
    }
}