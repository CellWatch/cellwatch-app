package edu.gatech.cc.cellwatch.core.util

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import edu.gatech.cc.cellwatch.CellWatchApp

object PermissionManager {
    fun checkPhoneStatePermission() : Boolean {
        val phonePermission = ContextCompat.checkSelfPermission(
            CellWatchApp.applicationContext(),
            Manifest.permission.READ_PHONE_STATE,
        ) == PackageManager.PERMISSION_GRANTED
        return phonePermission
    }

    fun checkLocationPermission() : Boolean {
        val context = CellWatchApp.applicationContext()
        val fineLocationPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val coarseLocationPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        return fineLocationPermission && coarseLocationPermission
    }
}
