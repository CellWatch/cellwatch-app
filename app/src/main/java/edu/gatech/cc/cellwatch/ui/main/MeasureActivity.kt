package edu.gatech.cc.cellwatch.ui.main

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.core.view.isVisible
import androidx.fragment.app.commit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import edu.gatech.cc.cellwatch.R
import edu.gatech.cc.cellwatch.databinding.ActivityMeasureBinding
import edu.gatech.cc.cellwatch.domain.fcc.MeasurementService
import kotlinx.coroutines.launch
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AlertDialog
import android.provider.Settings
import android.net.Uri
import android.os.Build
import android.util.Log


class MeasureActivity : AppCompatActivity() {
    private val TAG = this::class.simpleName
    private lateinit var binding: ActivityMeasureBinding
    private lateinit var model: MeasureViewModel
    private lateinit var backPressedCallback: OnBackPressedCallback
    private var boundToService = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMeasureBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Log.i("MeasureActivity", "ensureForegroundserviceLocationPermission")
        ensureForegroundServiceLocationPermission()

        binding.navDrawer.setOnCloseListener { binding.root.closeDrawer(GravityCompat.START) }
        binding.navDrawer.setActiveActivity(this)
        setSupportActionBar(binding.toolbar)
        binding.toolbar.setNavigationOnClickListener {binding.root.openDrawer(GravityCompat.START) }

        model = ViewModelProvider(this)[MeasureViewModel::class.java]
        backPressedCallback = onBackPressedDispatcher.addCallback(this, enabled = false) { /* do nothing! */ }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.state.collect { handleMeasureStateUpdate(it.progress) }
            }
        }
    }

    private fun ensureForegroundServiceLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.FOREGROUND_SERVICE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED) {
            AlertDialog.Builder(this)
                .setTitle(R.string.permission_required)
                .setMessage(R.string.fgs_location_permission_rationale)
                .setPositiveButton(R.string.open_settings) { _, _ ->
                    val intent = Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", packageName, null)
                    )
                    startActivity(intent)
                }
                .setCancelable(false)
                .show()
        }
    }

    override fun onStop() {
        super.onStop()
        unbindFromService()
    }

    fun bindToService() {
        if (boundToService) return
        val intent = Intent(this, MeasurementService::class.java)
        bindService(intent, model.serviceConnection, Context.BIND_AUTO_CREATE)
        boundToService = true
    }

    private fun unbindFromService() {
        if (!boundToService) return
        unbindService(model.serviceConnection)
        boundToService = false
    }

    private fun handleMeasureStateUpdate(progress: MeasureViewModel.MeasureProgress) {
        if (isFinishing) {
            return
        }

        val currentFragment = supportFragmentManager.findFragmentById(R.id.fragment_container)

        when (progress) {
            MeasureViewModel.MeasureProgress.PRE,
            MeasureViewModel.MeasureProgress.END,
            MeasureViewModel.MeasureProgress.ERROR -> unbindFromService()
            else -> bindToService()
        }

        when (progress) {
            MeasureViewModel.MeasureProgress.PRE -> if (currentFragment !is PreMeasureFragment) {
                supportFragmentManager.commit { replace(R.id.fragment_container, PreMeasureFragment()) }
            }
            else -> if (currentFragment !is MeasureFragment){
                supportFragmentManager.commit { replace(R.id.fragment_container, MeasureFragment()) }
            }
        }

        // Back does nothing from the start of a test until the next one is set up. This follows the
        // state rather than the fragment change, so it still holds when the activity is recreated
        // mid-test, e.g. when it's rotated on a large screen, where Android 16 ignores the portrait lock.
        backPressedCallback.isEnabled = progress != MeasureViewModel.MeasureProgress.PRE

        binding.toolbar.isVisible = progress === MeasureViewModel.MeasureProgress.PRE
    }
}