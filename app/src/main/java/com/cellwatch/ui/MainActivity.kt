package com.cellwatch.ui

import android.os.Bundle
import android.util.Log
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import com.cellwatch.R
import com.cellwatch.data.datastore.LocalDataStore
import com.cellwatch.databinding.ActivityMainBinding
import com.cellwatch.ui.measurement.viewmodels.MeasurementViewModel
import com.cellwatch.ui.measurement.viewmodels.MeasurementViewModelFactory
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.UUID

class MainActivity : AppCompatActivity() {
    private val TAG = "MainActivity"

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    val measurementRepository = com.cellwatch.CellWatchApp.measurementRepository

    private val measurementViewModel: MeasurementViewModel by viewModels() {
        MeasurementViewModelFactory(com.cellwatch.CellWatchApp.measurementRepository)
    }
//    private lateinit var measurementViewModel: MeasurementViewModel
//    private lateinit var measurementViewModelFactory: MeasurementViewModelFactory

//    private val measurementViewModel: MeasurementViewModel by viewModels<MeasurementViewModel> {
//        MeasurementViewModel.Factory
//    }
//    private val measurementViewModel: MeasurementViewModel by viewModels {
//        MeasurementViewModelFactory(CellWatchApp.measurementRepository)
////        MeasurementViewModelFactory((application as CellWatchApp).measurementRepository)
//    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val dataStore = LocalDataStore(this)

        // Create new deviceId on first run of app
        var deviceId = runBlocking {
            dataStore.getDeviceId.first()
        }

        // If there is no deviceId stored, assume first run of app and create a new, unique ID
        if (deviceId == "") {
            runBlocking {
                deviceId = UUID.randomUUID().toString()
                dataStore.saveDeviceId(deviceId)
            }
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        val navController = findNavController(R.id.nav_host_fragment_content_main)
        appBarConfiguration = AppBarConfiguration(navController.graph)
        setupActionBarWithNavController(navController, appBarConfiguration)

//        measurementViewModelFactory = MeasurementViewModelFactory(CellWatchApp.measurementRepository)
//        measurementViewModelFactory = MeasurementViewModelFactory((application as CellWatchApp).measurementRepository)
//        measurementViewModel = ViewModelProvider(this, measurementViewModelFactory).get(MeasurementViewModel::class.java)

        // Add an observer on the LiveData returned by getMeasurementsFlow.
        // The onChanged() method fires when the observed data changes and the activity is
        // in the foreground.
//        measurementViewModel.allMeasurements.observe(this) { measurements ->
//            // Update the cached copy of the measurements in the adapter.
//            measurements.let {
//                // attempt to upload measurements
//            }
//        }
    }

    override fun onResume() {
        super.onResume()
        val globalRoutine = GlobalScope.launch {
            try {
                measurementRepository.uploadMeasurements()
            } catch (err: Exception) {
                Log.e(TAG, "Error in MainActivity.onResume: uploadMeasurement error ${err.message}")
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration)
                || super.onSupportNavigateUp()
    }
}