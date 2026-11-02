package edu.gatech.cc.cellwatch.ui.main

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import edu.gatech.cc.cellwatch.CellWatchApp
import edu.gatech.cc.cellwatch.R
import edu.gatech.cc.cellwatch.core.util.Log
import edu.gatech.cc.cellwatch.data.model.CollectionMode
import edu.gatech.cc.cellwatch.databinding.FragmentPreMeasureBinding
import edu.gatech.cc.cellwatch.domain.fcc.MeasurementManager
import edu.gatech.cc.cellwatch.domain.fcc.MeasurementService
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class PreMeasureFragment: Fragment() {
    private val TAG = this::class.simpleName
    private lateinit var binding: FragmentPreMeasureBinding
    private lateinit var model: MeasureViewModel
    // Set from a tap on Go until the tap comes to nothing. One that starts the test leaves it set,
    // as the test's screen replaces this one. Two quick taps would otherwise both start the service.
    private var handlingGo = false
    private var inVehicle = false
        set(v) {
            field = v

            binding.stationary.isSelected = !v
            binding.stationaryCheck.isVisible = !v
            if (v) {
                binding.stationaryIcon.setColorFilter(resources.getColor(R.color.cw_grey, null))
            } else {
                binding.stationaryIcon.setColorFilter(resources.getColor(R.color.cw_green, null))
            }

            binding.moving.isSelected = v
            binding.movingCheck.isVisible = v
            if (v) {
                binding.movingIcon.setColorFilter(resources.getColor(R.color.cw_green, null))
            } else {
                binding.stationaryIcon.setColorFilter(resources.getColor(R.color.cw_grey, null))
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentPreMeasureBinding.inflate(inflater, container, false)
        model = ViewModelProvider(requireActivity())[MeasureViewModel::class.java]

        binding.stationary.setOnClickListener { model.setInVehicle(false) }
        binding.moving.setOnClickListener { model.setInVehicle(true) }
        binding.go.setOnClickListener { startMeasurement() }

        // enable clicking on link to show privacy policy
        binding.dataReminder.movementMethod = LinkMovementMethod.getInstance()

        inVehicle = false
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                val mode = CellWatchApp.settingsRepository.getCollectionMode()
                binding.stationaryOrMoving.isVisible = mode === CollectionMode.FCC_CHALLENGE
                binding.certifications.isVisible = mode === CollectionMode.FCC_CHALLENGE
                binding.testingWarning.isVisible = mode !== CollectionMode.FCC_CHALLENGE

                model.state.collect { inVehicle = it.inVehicle }
            }
        }

        return binding.root
    }

    // Whatever the answer, the test goes ahead; the notification only shows that one is running.
    // Android also hands the answer to the app after killing and restarting it, but the choice of
    // stationary or moving didn't survive that, so then the person taps Go again.
    private val requestNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (model.askingForNotifications) {
            model.askingForNotifications = false
            startMeasurement(askedForNotifications = true)
        }
    }

    private fun startMeasurement(askedForNotifications: Boolean = false) {
        if (handlingGo) return
        handlingGo = true
        lifecycleScope.launch { handlingGo = startMeasurementService(askedForNotifications) }
    }

    /** Starts the test unless a permission or the person's answer stops it. Says whether it did. */
    private suspend fun startMeasurementService(askedForNotifications: Boolean): Boolean {
        if (!hasForegroundServiceLocationPermission()) {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.permission_required)
                .setMessage(R.string.fgs_location_permission_rationale)
                .setPositiveButton(R.string.open_settings) { _, _ ->
                    val intent = Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", requireContext().packageName, null)
                    )
                    startActivity(intent)
                }
                .setCancelable(true)
                .show()
            return false
        }

        // From Android 13 the app has to ask before it can show the "measurement in progress"
        // notification. After two refusals Android answers for the person without asking.
        if (!askedForNotifications && !hasNotificationPermission()) {
            // While Android is asking, it answers another request at once as a refusal, so a
            // second tap on Go would start the test behind the question.
            if (!model.askingForNotifications) {
                model.askingForNotifications = true
                requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            return false
        }

        val mode = CellWatchApp.settingsRepository.getCollectionMode()
        if (
            mode == CollectionMode.FCC_CHALLENGE
            && !MeasurementManager.checkCellular()
            && !checkProceedWithoutCellular()
        ) {
            return false
        }

        val intent = Intent(requireContext(), MeasurementService::class.java)
        intent.putExtra(MeasurementService.EXTRA_IN_VEHICLE, model.state.value.inVehicle)
        intent.putExtra(MeasurementService.EXTRA_COLLECTION_MODE, mode.name)
        requireContext().startForegroundService(intent)

        val parent = activity
        if (parent is MeasureActivity) {
            parent.bindToService()
        } else {
            Log.e(TAG, "expected MeasureActivity, got $parent")
        }
        return true
    }

    private fun hasForegroundServiceLocationPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
                (
                        ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.FOREGROUND_SERVICE_LOCATION) == PackageManager.PERMISSION_GRANTED &&
                                (
                                        ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                                                ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                                        )
                        )
    }

    private fun hasNotificationPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    private suspend fun checkProceedWithoutCellular(): Boolean = suspendCoroutine { continuation ->
        AlertDialog.Builder(requireContext())
            .setMessage(R.string.not_cellular_warning)
            .setPositiveButton(R.string.not_cellular_proceed) { dialog, _ ->
                dialog.dismiss()
                continuation.resume(true)
            }
            .setNegativeButton(R.string.cancel) { dialog, _ ->
                dialog.dismiss()
                continuation.resume(false)
            }
            .show()
    }
}