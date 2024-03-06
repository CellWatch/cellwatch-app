package edu.gatech.cc.cellwatch.core.util

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import edu.gatech.cc.cellwatch.CellWatchApp

object PermissionManager {
    fun checkPermission() : Boolean {
        val phonePermission = ContextCompat.checkSelfPermission(CellWatchApp.applicationContext(),
            Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        Log.d("PermissionManager", "Permissions granted = $phonePermission")
        return phonePermission
    }
}
