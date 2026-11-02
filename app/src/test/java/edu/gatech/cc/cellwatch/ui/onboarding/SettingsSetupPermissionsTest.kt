package edu.gatech.cc.cellwatch.ui.onboarding

import android.Manifest.permission.ACCESS_COARSE_LOCATION
import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.Manifest.permission.READ_PHONE_STATE
import edu.gatech.cc.cellwatch.R
import org.junit.Assert.assertEquals
import org.junit.Test

/** What the onboarding Permissions screen asks Android for, and how it names what was refused. */
class SettingsSetupPermissionsTest {
    @Test
    fun aFirstRunAsksForEverything() {
        val all = listOf(ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION, READ_PHONE_STATE)
        assertEquals(all, permissionsToRequest(all))
    }

    @Test
    fun approximateLocationIsUpgradedByAskingForBothLocationPermissions() {
        // Coarse is granted, so only fine is missing; Android shows its upgrade dialog only for both.
        assertEquals(listOf(ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION), permissionsToRequest(listOf(ACCESS_FINE_LOCATION)))
        assertEquals(
            listOf(ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION, READ_PHONE_STATE),
            permissionsToRequest(listOf(ACCESS_FINE_LOCATION, READ_PHONE_STATE)),
        )
    }

    @Test
    fun phoneStateAloneIsAskedForAlone() {
        assertEquals(listOf(READ_PHONE_STATE), permissionsToRequest(listOf(READ_PHONE_STATE)))
    }

    @Test
    fun refusedLocationAndPhoneAreNamed() {
        assertEquals(
            listOf(R.string.permission_location, R.string.permission_phone),
            deniedPermissionNames(listOf(ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION, READ_PHONE_STATE)),
        )
    }

    @Test
    fun approximateLocationIsNamedAsPreciseLocation() {
        assertEquals(listOf(R.string.permission_precise_location), deniedPermissionNames(listOf(ACCESS_FINE_LOCATION)))
    }

    @Test
    fun refusedPhoneAloneIsNamed() {
        assertEquals(listOf(R.string.permission_phone), deniedPermissionNames(listOf(READ_PHONE_STATE)))
    }
}
