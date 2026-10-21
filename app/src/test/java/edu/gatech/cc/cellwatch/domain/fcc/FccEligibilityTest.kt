package edu.gatech.cc.cellwatch.domain.fcc

import edu.gatech.cc.cellwatch.data.model.CollectionMode
import edu.gatech.cc.cellwatch.data.model.LatencyData
import edu.gatech.cc.cellwatch.data.model.Location
import edu.gatech.cc.cellwatch.data.model.Measurement
import edu.gatech.cc.cellwatch.data.model.UploadDownloadData
import edu.gatech.cc.cellwatch.domain.telephony.managers.NetworkConnectionType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FccEligibilityTest {
    private val start = Location(id = "start", lat = 33.7756, lon = -84.3963)
    private val end = Location(id = "end", lat = 33.7760, lon = -84.3970)
    private val retried = Location(id = "retried", lat = 33.7758, lon = -84.3965)

    @Test
    fun capturedLocationsAreKeptWithoutRetrying() = runBlocking<Unit> {
        var retries = 0
        val retry: suspend () -> Location? = { retries++; retried }

        assertEquals(listOf(start, end), locationsOrRetry(start, end, retry))
        assertEquals(listOf(start), locationsOrRetry(start, null, retry))
        assertEquals(listOf(end), locationsOrRetry(null, end, retry))
        assertEquals(0, retries)
    }

    @Test
    fun locationIsRetriedOnceWhenNoneWasCaptured() = runBlocking<Unit> {
        var retries = 0

        val locations = locationsOrRetry(null, null) { retries++; retried }

        assertEquals(listOf(retried), locations)
        assertEquals(1, retries)
    }

    @Test
    fun noLocationsWhenTheRetryFindsNoneEither() = runBlocking<Unit> {
        assertEquals(emptyList<Location>(), locationsOrRetry(null, null) { null })
    }

    @Test
    fun cellularChallengeTestWithLocationsIsSubmitted() {
        assertTrue(isFccSubmittable(CollectionMode.FCC_CHALLENGE, latency(), download(), upload()))
    }

    @Test
    fun testWithAPartMissingItsLocationIsNotSubmitted() {
        val mode = CollectionMode.FCC_CHALLENGE

        assertFalse(isFccSubmittable(mode, latency(locations = emptyList()), download(), upload()))
        assertFalse(isFccSubmittable(mode, latency(), download(locations = null), upload()))
        assertFalse(isFccSubmittable(mode, latency(), download(), upload(locations = emptyList())))
    }

    @Test
    fun testingModeWifiAndDisabledDataAreStillNotSubmitted() {
        assertFalse(isFccSubmittable(CollectionMode.TESTING, latency(), download(), upload()))
        assertFalse(
            isFccSubmittable(
                CollectionMode.FCC_CHALLENGE,
                latency(),
                download(connectionType = NetworkConnectionType.WIFI),
                upload(),
            )
        )
        assertFalse(
            isFccSubmittable(
                CollectionMode.FCC_CHALLENGE,
                latency(),
                download(),
                upload(cellularDataEnabled = false),
            )
        )
    }

    private fun latency(locations: List<Location>? = listOf(start, end)) =
        measurement("latency", locations, NetworkConnectionType.CELLULAR, true)

    private fun download(
        locations: List<Location>? = listOf(start, end),
        connectionType: NetworkConnectionType = NetworkConnectionType.CELLULAR,
    ) = measurement("download", locations, connectionType, true)

    private fun upload(
        locations: List<Location>? = listOf(start, end),
        cellularDataEnabled: Boolean = true,
    ) = measurement("upload", locations, NetworkConnectionType.CELLULAR, cellularDataEnabled)

    private fun measurement(
        type: String,
        locations: List<Location>?,
        connectionType: NetworkConnectionType,
        cellularDataEnabled: Boolean,
    ) = Measurement(
        id = "g1-$type",
        groupId = "g1",
        type = type,
        latencyData = if (type == "latency") LatencyData() else null,
        uploadDownloadData = if (type != "latency") UploadDownloadData() else null,
        locations = locations,
        connectionType = connectionType,
        cellularDataEnabled = cellularDataEnabled,
    )
}
