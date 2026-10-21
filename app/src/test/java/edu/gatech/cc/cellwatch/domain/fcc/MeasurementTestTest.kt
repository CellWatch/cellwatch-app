package edu.gatech.cc.cellwatch.domain.fcc

import edu.gatech.cc.cellwatch.data.model.Cell
import edu.gatech.cc.cellwatch.data.model.Location
import edu.gatech.cc.cellwatch.data.model.Measurement
import edu.gatech.cc.cellwatch.domain.telephony.managers.NetworkConnectionType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests [MeasurementTest.run]: how the success of the latency/throughput result becomes the
 * measurement's success flag, and the FCC rule that a test must stay on one technology generation.
 */
class MeasurementTestTest {
    private val start = Instant.fromEpochMilliseconds(1_790_000_000_000)
    private val lte = Cell(cellId = 1, networkGeneration = "4G", cellConnection = 1)
    private val lteSecondary = Cell(cellId = 2, networkGeneration = "4G", cellConnection = 2)
    private val nr = Cell(cellId = 3, networkGeneration = "5G", cellConnection = 1)

    // --- result success is copied to the measurement ---

    @Test
    fun successfulLatencyResultIsASuccessfulMeasurement() = runBlocking<Unit> {
        val env = FakeEnvironment()

        val m = test("latency", env) { latency(success = true) }.run()

        assertEquals(true, m.success)
        assertEquals("latency", m.type)
        assertEquals("group-1", m.groupId)
        assertEquals(start, m.timestamp)
        assertEquals(2_000_000L, m.duration)
        assertEquals(30_000, m.latencyData?.rtt)
        assertEquals(2_000, m.latencyData?.jitter)
        assertEquals(10, m.latencyData?.sent)
        assertEquals(9, m.latencyData?.received)
        assertEquals(listOf("latency.example"), m.latencyData?.servers)
        assertNull(m.uploadDownloadData)
    }

    @Test
    fun failedLatencyResultIsAFailedMeasurement() = runBlocking<Unit> {
        val m = test("latency", FakeEnvironment()) { latency(success = false) }.run()

        assertEquals(false, m.success)
        assertEquals("latency data is still recorded", 10, m.latencyData?.sent)
    }

    @Test
    fun unreachableLatencyServerIsAFailedMeasurement() = runBlocking<Unit> {
        val m = test("latency", FakeEnvironment()) { LatencyTest.unreachableResult("locate.example", start) }.run()

        assertEquals(false, m.success)
        assertEquals(listOf("locate.example"), m.latencyData?.servers)
        assertEquals(0, m.latencyData?.received)
    }

    @Test
    fun successfulDownloadResultIsASuccessfulMeasurementWithItsThroughput() = runBlocking<Unit> {
        val m = test("download", FakeEnvironment()) { throughput(success = true) }.run()

        assertEquals(true, m.success)
        assertEquals("download", m.type)
        assertEquals(start, m.timestamp)
        assertEquals("warmup + active", 12_000_000L, m.duration)
        val data = m.uploadDownloadData!!
        assertEquals(2_000_000L, data.warmupDuration)
        assertEquals(1_000L, data.warmupBytes)
        assertEquals(10_000_000L, data.duration)
        assertEquals(6_000L, data.bytes)
        assertEquals(600.0, data.bytesPerSec!!, 0.0)
        assertEquals(listOf("throughput.example"), data.servers)
        assertNull(m.latencyData)
    }

    @Test
    fun failedUploadResultIsAFailedMeasurement() = runBlocking<Unit> {
        val m = test("upload", FakeEnvironment()) { throughput(success = false) }.run()

        assertEquals(false, m.success)
        assertEquals("upload", m.type)
    }

    @Test
    fun unreachableThroughputServerIsAFailedMeasurement() = runBlocking<Unit> {
        val m = test("download", FakeEnvironment()) { ThroughputTest.unreachableResult("locate.example", start) }.run()

        assertEquals(false, m.success)
        assertEquals(0L, m.uploadDownloadData?.bytes)
        assertEquals(0.0, m.uploadDownloadData?.bytesPerSec!!, 0.0)
    }

    // --- the test must stay on one technology generation ---

    @Test
    fun cellUpdatesOnTheSameGenerationKeepASuccessfulResult() = runBlocking<Unit> {
        val env = FakeEnvironment(cells = listOf(lte), generation = "4G")

        val m = test("download", env) {
            env.cellsChange(listOf(lte, lteSecondary), "4G")
            env.cellsChange(listOf(lte), "4G")
            throughput(success = true)
        }.run()

        assertEquals(true, m.success)
    }

    @Test
    fun generationChangeDuringTheTestFailsASuccessfulResult() = runBlocking<Unit> {
        val env = FakeEnvironment(cells = listOf(lte), generation = "4G")

        val m = test("download", env) {
            env.cellsChange(listOf(nr), "5G")
            throughput(success = true)
        }.run()

        assertEquals(false, m.success)
    }

    @Test
    fun generationThatChangesAndComesBackStillFails() = runBlocking<Unit> {
        val env = FakeEnvironment(cells = listOf(lte), generation = "4G")

        val m = test("latency", env) {
            env.cellsChange(listOf(Cell(cellId = 9, networkGeneration = "3G")), "3G")
            env.cellsChange(listOf(lte), "4G")
            latency(success = true)
        }.run()

        assertEquals(false, m.success)
    }

    @Test
    fun losingTheCellularNetworkDuringTheTestFails() = runBlocking<Unit> {
        val env = FakeEnvironment(cells = listOf(lte), generation = "4G")

        val m = test("upload", env) {
            env.cellsChange(emptyList(), null)
            throughput(success = true)
        }.run()

        assertEquals(false, m.success)
    }

    @Test
    fun generationChangeCannotMakeAFailedResultSuccessful() = runBlocking<Unit> {
        val env = FakeEnvironment(cells = listOf(lte), generation = "4G")

        val m = test("download", env) {
            env.cellsChange(listOf(nr), "5G")
            throughput(success = false)
        }.run()

        assertEquals(false, m.success)
    }

    @Test
    fun generationThatIsUnknownThroughoutLeavesTheResultSuccess() = runBlocking<Unit> {
        // getActiveNetworkGeneration() is null when the active network isn't cellular (e.g. WiFi)
        // or the phone state permission is missing. A consistently unknown generation is not
        // treated as a change; WiFi tests are kept out of FCC submissions by MeasurementManager.
        val env = FakeEnvironment(generation = null, connectionType = NetworkConnectionType.WIFI)

        val m = test("latency", env) { latency(success = true) }.run()

        assertEquals(true, m.success)
        assertEquals(NetworkConnectionType.WIFI, m.connectionType)
    }

    @Test
    fun generationChangeByTheEndOfTheTestFailsEvenWithoutACellUpdate() = runBlocking<Unit> {
        // The FCC requires the same generation at the start and at the end of the test. Cell info
        // callbacks are not guaranteed to arrive before the watcher is stopped, so the generation
        // at the end has to be checked directly.
        val env = FakeEnvironment(cells = listOf(lte), generation = "4G")

        val m = test("download", env) {
            env.generation = "5G"
            throughput(success = true)
        }.run()

        assertEquals(false, m.success)
    }

    // --- errors and cancellation ---

    @Test
    fun errorInTheMeasurementPropagatesAndStopsWatchingCells() {
        val env = FakeEnvironment()
        val test = test("download", env) { throw IllegalStateException("boom") }

        val e = assertThrows(IllegalStateException::class.java) { runBlocking { test.run() } }

        assertEquals("boom", e.message)
        assertEquals(1, env.stopCount)
        assertEquals("no end location is requested", 1, env.locationRequests)
    }

    @Test
    fun cancellationPropagatesAndStopsWatchingCells() {
        val env = FakeEnvironment()
        val test = test("upload", env) { throw CancellationException("user left") }

        assertThrows(CancellationException::class.java) { runBlocking { test.run() } }

        assertEquals(1, env.stopCount)
    }

    @Test
    fun cellWatcherIsStoppedOnceTheMeasurementFinishes() = runBlocking<Unit> {
        val env = FakeEnvironment()
        var watchingDuringMeasurement = false

        test("latency", env) {
            watchingDuringMeasurement = env.watching
            latency(success = true)
        }.run()

        assertTrue(watchingDuringMeasurement)
        assertFalse(env.watching)
        assertEquals(1, env.stopCount)
    }

    @Test
    fun latencyTestThatReturnsAThroughputResultIsRejected() {
        val env = FakeEnvironment()
        val test = test("latency", env) { throughput(success = true) }

        assertThrows(Exception::class.java) { runBlocking { test.run() } }
        assertEquals(1, env.stopCount)
    }

    @Test
    fun throughputTestThatReturnsALatencyResultIsRejected() {
        val test = test("download", FakeEnvironment()) { latency(success = true) }

        assertThrows(Exception::class.java) { runBlocking { test.run() } }
    }

    @Test
    fun throughputTestThatReturnsSomethingElseIsRejected() {
        val test = test("upload", FakeEnvironment()) { "not a result" }

        assertThrows(Exception::class.java) { runBlocking { test.run() } }
    }

    @Test
    fun unknownTestTypeIsRejected() {
        assertThrows(Exception::class.java) { test("jitter", FakeEnvironment()) { latency(success = true) } }
    }

    // --- missing location and cell info ---

    @Test
    fun missingLocationFixesDoNotAffectSuccess() = runBlocking<Unit> {
        val env = FakeEnvironment(locations = mutableListOf(null, null))

        val m = test("download", env) { throughput(success = true) }.run()

        assertEquals(true, m.success)
        assertEquals(emptyList<Location>(), m.locations)
    }

    @Test
    fun onlyTheLocationsThatWereFoundAreRecorded() = runBlocking<Unit> {
        val end = Location(lat = 33.77, lon = -84.39)
        val env = FakeEnvironment(locations = mutableListOf(null, end))

        val m = test("latency", env) { latency(success = true) }.run()

        assertEquals(true, m.success)
        assertEquals(listOf(end), m.locations)
    }

    @Test
    fun beginAndEndLocationsAreRecordedInOrder() = runBlocking<Unit> {
        val begin = Location(lat = 33.77, lon = -84.39)
        val end = Location(lat = 33.78, lon = -84.40)
        val env = FakeEnvironment(locations = mutableListOf(begin, end))

        val m = test("latency", env) { latency(success = true) }.run()

        assertEquals(listOf(begin, end), m.locations)
    }

    @Test
    fun locationErrorBeforeTheTestFailsTheRunWithoutMeasuring() {
        // AndroidMeasurementEnvironment.getLocation() throws when location permission is missing.
        val env = FakeEnvironment(locationError = Exception("no location permission"))
        val test = test("latency", env) { latency(success = true) }

        val e = assertThrows(Exception::class.java) { runBlocking { test.run() } }

        assertEquals("no location permission", e.message)
        assertFalse(test.measured)
        assertEquals("cells are never watched", 0, env.watchCount)
    }

    @Test
    fun missingCellInfoDoesNotAffectSuccess() = runBlocking<Unit> {
        val env = FakeEnvironment(cells = null)

        val m = test("upload", env) { throughput(success = true) }.run()

        assertEquals(true, m.success)
        assertEquals(emptyList<Cell>(), m.cells)
        assertEquals(false, m.carrierAggregation)
    }

    @Test
    fun cellsSeenDuringTheTestAreRecordedAndUsedForCarrierAggregation() = runBlocking<Unit> {
        val env = FakeEnvironment(cells = listOf(lte), generation = "4G")

        val m = test("download", env) {
            env.cellsChange(listOf(lte, lteSecondary), "4G")
            throughput(success = true)
        }.run()

        assertEquals(listOf(lte, lte, lteSecondary), m.cells)
        assertEquals(true, m.carrierAggregation)
        assertEquals(listOf(lte, lte, lteSecondary), env.carrierAggregationCells)
    }

    @Test
    fun deviceAndNetworkDetailsAreRecorded() = runBlocking<Unit> {
        val env = FakeEnvironment(cellularDataEnabled = false)

        val m = test("latency", env) { latency(success = true) }.run()

        assertEquals("device-1", m.deviceId)
        assertEquals("Maker", m.deviceManufacturer)
        assertEquals("Model", m.deviceModel)
        assertEquals("Android", m.deviceOsName)
        assertEquals("15", m.deviceOsVersion)
        assertEquals("CellWatch", m.appName)
        assertEquals("Carrier", m.provider)
        assertEquals("310", m.simMcc)
        assertEquals("260", m.simMnc)
        assertEquals("311", m.netMcc)
        assertEquals("480", m.netMnc)
        assertEquals(true, m.networkAvailable)
        assertEquals(true, m.networkConnected)
        assertEquals(false, m.networkRoaming)
        assertEquals(false, m.scheduled)
        assertEquals(NetworkConnectionType.CELLULAR, m.connectionType)
        assertEquals(false, m.cellularDataEnabled)
    }

    // --- helpers ---

    private fun latency(success: Boolean) =
        LatencyResult("latency.example", success, start, 2_000_000, 30_000, 2_000, 10, 9)

    private fun throughput(success: Boolean) = ThroughputResult(
        "throughput.example",
        success,
        start,
        ThroughputMetrics(1_000, 2_000_000, 500.0),
        ThroughputMetrics(6_000, 10_000_000, 600.0),
    )

    private fun test(type: String, env: MeasurementEnvironment, outcome: suspend () -> Any) =
        FakeTest(type, env, outcome)

    private class FakeTest(
        type: String,
        env: MeasurementEnvironment,
        private val outcome: suspend () -> Any,
    ) : MeasurementTest<Any>("group-1", type, env) {
        var measured = false

        override suspend fun measure(): Any {
            measured = true
            return outcome()
        }
    }

    private class FakeEnvironment(
        private val cells: List<Cell>? = emptyList(),
        var generation: String? = "4G",
        val locations: MutableList<Location?> = mutableListOf(),
        val locationError: Throwable? = null,
        private val connectionType: NetworkConnectionType = NetworkConnectionType.CELLULAR,
        private val cellularDataEnabled: Boolean? = true,
    ) : MeasurementEnvironment {
        private var onChange: ((List<Cell>) -> Unit)? = null
        var watching = false
        var watchCount = 0
        var stopCount = 0
        var locationRequests = 0
        var carrierAggregationCells: List<Cell>? = null

        /** Simulates the phone reporting new cells, which happens along with a generation change. */
        fun cellsChange(newCells: List<Cell>, newGeneration: String?) {
            generation = newGeneration
            if (watching) onChange?.invoke(newCells)
        }

        override suspend fun getLocation(): Location? {
            locationRequests++
            locationError?.let { throw it }
            return locations.removeFirstOrNull()
        }

        override fun getConnectionType() = connectionType
        override fun isCellularDataEnabled() = cellularDataEnabled
        override fun getCells() = cells
        override fun getActiveNetworkGeneration() = generation

        override fun watchCells(onChange: (List<Cell>) -> Unit): () -> Unit {
            this.onChange = onChange
            watching = true
            watchCount++
            return {
                watching = false
                stopCount++
            }
        }

        override suspend fun getDeviceId() = "device-1"
        override fun getDeviceManufacturer() = "Maker"
        override fun getDeviceModel() = "Model"
        override fun getDeviceOsVersion() = "15"
        override fun getAppName() = "CellWatch"
        override fun getProviderName() = "Carrier"
        override fun getSimMobileCountryCode() = "310"
        override fun getSimMobileNetworkCode() = "260"
        override fun getNetworkMobileCountryCode() = "311"
        override fun getNetworkMobileNetworkCode() = "480"

        override fun isUsingCarrierAggregation(cells: List<Cell>): Boolean {
            carrierAggregationCells = cells.toList()
            return cells.count { it.cellConnection == 1 || it.cellConnection == 2 } > 1
        }

        override fun isNetworkAvailable() = true
        override fun isNetworkConnected() = true
        override fun isNetworkRoaming() = false
    }
}
