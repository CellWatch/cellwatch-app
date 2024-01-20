import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.cellwatch.R
import com.cellwatch.ui.home.MapFragment

class SettingsSetupFragment : Fragment() {

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_onboarding_settings, container, false)

        val btnReady = view.findViewById<Button>(R.id.btnReady)
        btnReady.setOnClickListener {
            requestLocationPermission()
        }

        return view
    }

    private fun requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // Permission is not granted, so request it
            requestPermissions(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        } else {
            // Permission has already been granted, load the MapFragment
            loadMapFragment()
        }
    }


    private fun loadMapFragment() {
        // Replace the current fragment with the MapFragment
        fragmentManager?.beginTransaction()?.apply {
            (view?.parent as? ViewGroup)?.id?.let { replace(it, MapFragment()) }
            addToBackStack(null) // if you want to add the transaction to the back stack
            commit()
        }
    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    loadMapFragment()
                } else {
                    // Permission was denied. Handle the functionality that cannot proceed without the permission.
                }
            }
            else -> super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }
}
