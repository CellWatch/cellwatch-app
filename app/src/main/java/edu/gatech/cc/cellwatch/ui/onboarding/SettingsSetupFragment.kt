package edu.gatech.cc.cellwatch.ui.onboarding

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import edu.gatech.cc.cellwatch.R

class SettingsSetupFragment : Fragment() {

    interface OnPermissionsHandledListener {
        fun onPermissionsHandled()
    }

    companion object {
        private const val PERMISSIONS_REQUEST_CODE = 1
        private const val SENT_TO_SETTINGS = "sent_to_settings"
        // PermissionManager also checks both location permissions.
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.READ_PHONE_STATE
        )
    }

    // Set when the person leaves for the app's settings to allow the permissions, so the screen can
    // move on by itself once they come back with them allowed.
    private var sentToSettings = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sentToSettings = savedInstanceState?.getBoolean(SENT_TO_SETTINGS) ?: false
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_onboarding_settings, container, false)

        val btnReady = view.findViewById<Button>(R.id.btnReady)
        btnReady.setOnClickListener {
            requestPermissionsIfNeeded()
        }

        return view
    }

    override fun onResume() {
        super.onResume()
        if (sentToSettings) {
            sentToSettings = false
            val missing = missingPermissions()
            if (missing.isEmpty()) {
                (activity as? OnPermissionsHandledListener)?.onPermissionsHandled()
            } else {
                showPermissionsDenied(missing)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(SENT_TO_SETTINGS, sentToSettings)
    }

    private fun missingPermissions(): List<String> {
        val context = requireContext()
        return REQUIRED_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissionsIfNeeded() {
        val missingPermissions = missingPermissions()

        if (missingPermissions.isNotEmpty()) {
            // Permissions are not granted, so request them
            requestPermissions(
                permissionsToRequest(missingPermissions).toTypedArray(),
                PERMISSIONS_REQUEST_CODE
            )
        } else {
            // All permissions are granted
            (activity as? OnPermissionsHandledListener)?.onPermissionsHandled()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            PERMISSIONS_REQUEST_CODE -> {
                // Empty results mean the request was interrupted rather than answered
                if (grantResults.isEmpty()) return

                val denied = permissions.filterIndexed { i, _ ->
                    grantResults[i] != PackageManager.PERMISSION_GRANTED
                }
                if (denied.isEmpty()) {
                    // All requested permissions are granted
                    (activity as? OnPermissionsHandledListener)?.onPermissionsHandled()
                } else {
                    showPermissionsDenied(denied)
                }
            }
            else -> super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    // Says what's missing and offers the app's settings, where it can still be allowed. Once a
    // person has refused twice, Android stops asking, so Ready alone would do nothing.
    private fun showPermissionsDenied(denied: List<String>) {
        val names = deniedPermissionNames(denied).joinToString("\n") { getString(it) }
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.permissions_needed)
            .setMessage(getString(R.string.permissions_denied, names))
            .setPositiveButton(R.string.open_settings) { _, _ ->
                sentToSettings = true
                startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", requireContext().packageName, null)
                    )
                )
            }
            .setNegativeButton(R.string.cancel_button, null)
            .show()
    }
}

/**
 * The permissions to ask Android for when [missing] aren't granted. Fine location is always asked
 * for together with coarse location, even when coarse is already granted: that's how a person who
 * chose approximate location is asked for precise location, and some releases of Android 12 ignore
 * a request for fine location on its own.
 */
internal fun permissionsToRequest(missing: List<String>): List<String> {
    val location = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    return if (missing.any { it in location }) (location + missing).distinct() else missing
}

/**
 * The names, as string resources, of the refused permissions in [denied]. Fine location refused
 * without coarse location means the person chose approximate location.
 */
internal fun deniedPermissionNames(denied: List<String>): List<Int> = listOfNotNull(
    when {
        Manifest.permission.ACCESS_COARSE_LOCATION in denied -> R.string.permission_location
        Manifest.permission.ACCESS_FINE_LOCATION in denied -> R.string.permission_precise_location
        else -> null
    },
    R.string.permission_phone.takeIf { Manifest.permission.READ_PHONE_STATE in denied },
)
