package edu.gatech.cc.cellwatch.androidtestapp.product

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Switch
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import edu.gatech.cc.cellwatch.androidtestapp.designsystem.Components
import edu.gatech.cc.cellwatch.androidtestapp.designsystem.ScreenScaffold
import edu.gatech.cc.cellwatch.androidtestapp.designsystem.Theme
import edu.gatech.cc.cellwatch.androidtestapp.designsystem.Theme.dp
import edu.gatech.cc.cellwatch.domain.measurementstart.MeasurementNetworkPath
import edu.gatech.cc.cellwatch.domain.measurementstart.MeasurementStartCapabilitySnapshot
import edu.gatech.cc.cellwatch.domain.measurementstart.MeasurementStartCopy
import edu.gatech.cc.cellwatch.domain.measurementstart.MeasurementStartUiState
import edu.gatech.cc.cellwatch.domain.measurementstart.MeasurementStartViewModel

/**
 * Pre-flight before a measurement. Android counterpart of
 * `MeasurementStartScreenViewController`.
 *
 * Same shared [MeasurementStartViewModel]; this reads what the platform can see
 * and renders the answer.
 */
class MeasurementStartScreen(
    private val context: Context,
    private val viewModel: MeasurementStartViewModel,
    /**
     * Supplied by the shell, which owns container resolution. Hardcoding true
     * here let the screen clear a gate it cannot actually see.
     */
    private val hasRuntimeProfile: Boolean,
    private val onReadyToRun: (Boolean) -> Unit,
) {

    private val inVehicleSwitch = Switch(context)
    private val statusLabel = Components.bodyText(context, "", muted = true)
    private val scaffold = ScreenScaffold(context)

    val view: View get() = scaffold

    init {
        val inVehicleRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = context.dp(Theme.MIN_TAP_TARGET_DP)
            addView(
                Components.bodyText(context, MeasurementStartCopy.IN_MOVING_VEHICLE),
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f),
            )
            addView(inVehicleSwitch)
        }
        inVehicleSwitch.setOnCheckedChangeListener { _, checked ->
            render(viewModel.setInVehicle(checked))
        }

        val startButton = Components.primaryButton(context, MeasurementStartCopy.TITLE).apply {
            setOnClickListener { render(viewModel.onStartPressed(observedCapabilities())) }
        }

        scaffold.addContent(
            Components.bodyText(
                context,
                MeasurementStartCopy.WHAT_HAPPENS,
                muted = true,
            ),
            Components.divider(context),
            inVehicleRow,
            Components.divider(context),
            statusLabel,
        )
        scaffold.addActions(startButton)
        render(viewModel.currentState())
    }

    /**
     * Read at press time rather than cached: permission and network can both
     * change while the screen is open.
     */
    private fun observedCapabilities() = MeasurementStartCapabilitySnapshot(
        hasRuntimeProfile = hasRuntimeProfile,
        hasLocationPermission = listOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ).all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED },
        networkPath = observedNetworkPath(),
    )

    private fun observedNetworkPath(): MeasurementNetworkPath {
        val cm = context.getSystemService(ConnectivityManager::class.java)
            ?: return MeasurementNetworkPath.UNKNOWN
        val active = cm.activeNetwork ?: return MeasurementNetworkPath.UNKNOWN
        val caps = cm.getNetworkCapabilities(active) ?: return MeasurementNetworkPath.UNKNOWN
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> MeasurementNetworkPath.CELLULAR
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> MeasurementNetworkPath.WIFI
            else -> MeasurementNetworkPath.UNKNOWN
        }
    }

    private fun render(state: MeasurementStartUiState) {
        inVehicleSwitch.isChecked = state.inVehicle
        statusLabel.text = state.statusMessage
        statusLabel.setTextColor(
            if (state.statusIsError) Theme.Palette.WARNING else Theme.Palette.TEXT_SECONDARY,
        )

        val confirmation = state.confirmationMessage
        if (state.shouldPromptConfirmation && confirmation != null) {
            AlertDialog.Builder(context)
                .setTitle(MeasurementStartCopy.BEFORE_YOU_START)
                .setMessage(confirmation)
                .setCancelable(false)
                // MeasurementStartCopy.MEASURE_ANYWAY rather than "Continue": the shared status
                // copy names that action, so the two must agree.
                .setPositiveButton(MeasurementStartCopy.MEASURE_ANYWAY) { _, _ -> render(viewModel.onConfirmProceed()) }
                .setNegativeButton("Cancel") { _, _ -> render(viewModel.onConfirmCancel()) }
                .show()
            return
        }
        if (state.readyToRun) onReadyToRun(state.inVehicle)
    }
}
