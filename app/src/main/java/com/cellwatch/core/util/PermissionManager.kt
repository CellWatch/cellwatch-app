package com.cellwatch.core.util

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.cellwatch.CellWatchApp

object PermissionManager {
    fun checkPermission() : Boolean {
//        val backgroundPermission = ContextCompat.checkSelfPermission(
//            CellWatchApp.applicationContext(),
//            Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        val phonePermission = ContextCompat.checkSelfPermission(CellWatchApp.applicationContext(),
            Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED
        Log.d("PermissionManager", "Permissions granted = $phonePermission")
        return phonePermission
//        return backgroundPermission && phonePermission
    }
}