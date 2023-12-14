package edu.gatech.cc.cellwatch.ui.main

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import edu.gatech.cc.cellwatch.CellWatchApp
import edu.gatech.cc.cellwatch.R
import edu.gatech.cc.cellwatch.data.datastore.LocalDataStore
import edu.gatech.cc.cellwatch.databinding.ActivityMainBinding
import edu.gatech.cc.cellwatch.domain.fcc.MeasurementManager
import edu.gatech.cc.cellwatch.domain.telephony.managers.TelephonyInfoManager
import edu.gatech.cc.cellwatch.ui.measurement.viewmodels.MeasurementViewModel
import edu.gatech.cc.cellwatch.ui.measurement.viewmodels.MeasurementViewModelFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.conscrypt.Conscrypt
import java.security.Security
import java.util.UUID

class MainActivity : AppCompatActivity() {
    private val TAG = "MainActivity"

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    val measurementRepository = CellWatchApp.measurementRepository
    val fccSubmissionRepository = CellWatchApp.fccSubmissionRepository

    private val measurementViewModel: MeasurementViewModel by viewModels() {
        MeasurementViewModelFactory(edu.gatech.cc.cellwatch.CellWatchApp.measurementRepository)
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

        try {
            // Using Conscrypt somehow makes the CountableSocket work with TLS sockets --
            // it doesn't otherwise. I found a project on GitHub trying to count socket bytes
            // (https://github.com/dave-r12/okhttp-byte-counter) and then found a linked
            // issue (https://github.com/google/conscrypt/issues/65) that suggests Conscrypt
            // might eventually solve the problem but hasn't yet. I guess it has now...
            Security.insertProviderAt(Conscrypt.newProvider(), 1)

            val dataStore = LocalDataStore(this)

            // Create new deviceId on first run of app
            var deviceId = runBlocking {
                dataStore.getDeviceId.first()
            }

            CoroutineScope(Dispatchers.Main).launch {
                val myPublicIp = TelephonyInfoManager.getMyPublicIpAsync().await()
                Toast.makeText(applicationContext, myPublicIp, Toast.LENGTH_LONG).show()
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
        } catch (e: Exception) {
            Log.e(TAG, "onCreate exception:", e)
            throw e
        }

//        val connectionType = TelephonyInfoManager.getConnectionType()
//        run {
//            Toast.makeText(applicationContext, "Connection type is ${connectionType.toString()}",
//                Toast.LENGTH_SHORT).show()
//        }

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
        Log.d(TAG, "MainActivity.onResume: uploadMeasurements")
        val globalRoutine = GlobalScope.launch {
            try {
                fccSubmissionRepository.uploadFccSubmissions()
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
