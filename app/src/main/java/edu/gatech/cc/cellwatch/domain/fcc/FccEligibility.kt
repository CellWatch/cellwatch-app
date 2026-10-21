package edu.gatech.cc.cellwatch.domain.fcc

import edu.gatech.cc.cellwatch.data.model.CollectionMode
import edu.gatech.cc.cellwatch.data.model.Location
import edu.gatech.cc.cellwatch.data.model.Measurement
import edu.gatech.cc.cellwatch.domain.telephony.managers.NetworkConnectionType

/**
 * Whether a finished test becomes an FCC submission. Only FCC Challenge mode tests taken over
 * cellular data are sent, and only when every part of the test has a location: the BDC rejects a
 * test without one. A test that is not submitted is still saved and uploaded as ordinary
 * measurements.
 */
fun isFccSubmittable(
    mode: CollectionMode,
    latency: Measurement,
    download: Measurement,
    upload: Measurement,
): Boolean {
    val measurements = listOf(latency, download, upload)

    return mode == CollectionMode.FCC_CHALLENGE &&
        measurements.all { it.connectionType != NetworkConnectionType.WIFI && it.cellularDataEnabled != false } &&
        measurements.all { !it.locations.isNullOrEmpty() }
}

/**
 * The locations to store with a measurement: the fixes taken at its start and end. If neither was
 * available, [retry] is called once so the test still gets a location the BDC will accept.
 */
suspend fun locationsOrRetry(
    begin: Location?,
    end: Location?,
    retry: suspend () -> Location?,
): List<Location> {
    val captured = listOfNotNull(begin, end)
    if (captured.isNotEmpty()) {
        return captured
    }

    return listOfNotNull(retry())
}
