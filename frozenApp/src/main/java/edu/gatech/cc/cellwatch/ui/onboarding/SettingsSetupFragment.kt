import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import edu.gatech.cc.cellwatch.R

class SettingsSetupFragment : Fragment() {

    interface OnPermissionsHandledListener {
        fun onPermissionsHandled()
    }

    companion object {
        private const val PERMISSIONS_REQUEST_CODE = 1
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.READ_PHONE_STATE
        )
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

    private fun requestPermissionsIfNeeded() {
        val context = requireContext()
        val missingPermissions = REQUIRED_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (missingPermissions.isNotEmpty()) {
            // Permissions are not granted, so request them
            requestPermissions(
                missingPermissions,
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
                if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                    // All requested permissions are granted
                    (activity as? OnPermissionsHandledListener)?.onPermissionsHandled()
                } else {
                    // At least one permission was denied
                    // TODO: Handle the case where permissions are denied
                }
            }
            else -> super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }
}
