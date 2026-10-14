package edu.gatech.cc.cellwatch.domain.fcc

import android.Manifest
import androidx.test.platform.app.InstrumentationRegistry

/**
 * MeasurementTest.run() needs the permissions the app asks for during onboarding: it throws without
 * location, and watching cells throws a SecurityException without phone state (seen on API 36). A
 * freshly installed test build has neither.
 */
fun grantMeasurementPermissions() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val packageName = instrumentation.targetContext.packageName
    listOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.READ_PHONE_STATE,
    ).forEach { instrumentation.uiAutomation.grantRuntimePermission(packageName, it) }
}
