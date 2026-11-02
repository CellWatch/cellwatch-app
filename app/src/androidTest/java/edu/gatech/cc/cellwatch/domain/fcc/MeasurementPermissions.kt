package edu.gatech.cc.cellwatch.domain.fcc

import android.Manifest
import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry

/**
 * MeasurementTest.run() needs the permissions the app asks for during onboarding: it throws without
 * location, and watching cells throws a SecurityException without phone state (seen on API 36). A
 * freshly installed test build has neither. From Android 13, notifications too, or Go stops at
 * Android's question about them.
 */
fun grantMeasurementPermissions() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val packageName = instrumentation.targetContext.packageName
    listOfNotNull(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.POST_NOTIFICATIONS.takeIf { Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU },
    ).forEach { instrumentation.uiAutomation.grantRuntimePermission(packageName, it) }
}
