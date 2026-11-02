package edu.gatech.cc.cellwatch.data.core.repositories

import edu.gatech.cc.cellwatch.data.local.dao.FccSubmissionDao
import edu.gatech.cc.cellwatch.data.local.dao.MeasurementDao
import edu.gatech.cc.cellwatch.data.local.model.CellEntity
import edu.gatech.cc.cellwatch.data.local.model.FccSubmissionEntity
import edu.gatech.cc.cellwatch.data.local.model.FccSubmissionWithMeasurements
import edu.gatech.cc.cellwatch.data.local.model.LatencyDataEntity
import edu.gatech.cc.cellwatch.data.local.model.LocationEntity
import edu.gatech.cc.cellwatch.data.local.model.MeasurementEntity
import edu.gatech.cc.cellwatch.data.local.model.MeasurementWithData
import edu.gatech.cc.cellwatch.data.local.model.UploadDownloadDataEntity
import edu.gatech.cc.cellwatch.data.model.FccSubmission
import edu.gatech.cc.cellwatch.data.model.Measurement
import edu.gatech.cc.cellwatch.data.model.MeasurementGroup
import edu.gatech.cc.cellwatch.data.model.TestRecords
import edu.gatech.cc.cellwatch.data.model.asEntity
import edu.gatech.cc.cellwatch.data.model.asEntityWithData
import edu.gatech.cc.cellwatch.data.network.NetworkMeasurementDatasource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MeasurementRepository.getMeasurementGroups turns what Room holds into the tests that the history
 * screen and the map list and the FCC export writes. A group is one test: one FCC submission (§5.1.2
 * of the FCC's mobile speed test data spec, July 28, 2025) whose Test Object (§5.1.3) holds its
 * latency, download and upload measurements.
 */
class MeasurementGroupsTest {
    @Test
    fun aTestsMeasurementsAndSubmissionMakeOneGroup() = runBlocking<Unit> {
        val groups = groups(test("g1"), listOf(TestRecords.submission("g1")))

        assertEquals(listOf(TestRecords.group("g1")), groups)
    }

    @Test
    fun eachTestIsAGroupInTheOrderItsFirstMeasurementWasSaved() = runBlocking<Unit> {
        val g1 = test("g1")
        val g2 = test("g2")

        val groups = groups(listOf(g2[0], g1[0], g2[1], g1[1], g2[2], g1[2]))

        assertEquals(listOf("g2", "g1"), groups.map { it.id })
        assertEquals(MeasurementGroup(g1[0], g1[1], g1[2], null), groups[1])
    }

    @Test
    fun aTestWithoutASubmissionIsListedWithoutOne() = runBlocking<Unit> {
        // Testing mode, or a test that didn't qualify for the FCC (on Wi-Fi, or with mobile data off).
        val groups = groups(test("g1") + test("g2"), listOf(TestRecords.submission("g2")))

        assertNull(groups.single { it.id == "g1" }.submission)
        assertEquals(TestRecords.submission("g2"), groups.single { it.id == "g2" }.submission)
    }

    @Test
    fun aTestThatStoppedPartWayKeepsWhatItSaved() = runBlocking<Unit> {
        // The latency test runs first. If the download then fails, the test stops there, with no
        // upload and no submission.
        val latency = TestRecords.measurement("latency", "g1")

        assertEquals(listOf(MeasurementGroup(latency, null, null, null)), groups(listOf(latency)))
    }

    @Test
    fun aSubmissionWithoutMeasurementsIsNotListed() = runBlocking<Unit> {
        val groups = groups(test("g1"), listOf(TestRecords.submission("g1"), TestRecords.submission("g2")))

        assertEquals(listOf("g1"), groups.map { it.id })
    }

    @Test
    fun aMeasurementWithoutAGroupIdIsSkippedWithAWarning() = runBlocking<Unit> {
        // It used to fail the whole list, which neither MeasureHistoryViewModel nor MapViewModel
        // catches, so one such row kept every test off the history screen and the map.
        val g1 = test("g1")
        val orphan = TestRecords.measurement("latency", "g2").copy(id = "orphan", groupId = null)
        val warnings = mutableListOf<String>()

        val groups = groups(g1 + orphan, warn = { warnings += it })

        assertEquals(listOf(MeasurementGroup(g1[0], g1[1], g1[2], null)), groups)
        assertEquals(1, warnings.size)
        assertTrue(warnings.single(), "orphan" in warnings.single())
    }

    @Test
    fun aMeasurementOfAnUnknownTypeIsSkippedAndItsTestKeepsTheRest() = runBlocking<Unit> {
        val unknown = TestRecords.measurement("upload", "g2").copy(id = "g2-jitter", type = "jitter")
        val latency = TestRecords.measurement("latency", "g2")
        val warnings = mutableListOf<String>()

        val groups = groups(test("g1") + latency + unknown, warn = { warnings += it })

        assertEquals(listOf("g1", "g2"), groups.map { it.id })
        assertEquals(MeasurementGroup(latency, null, null, null), groups[1])
        assertTrue(warnings.single(), "g2-jitter" in warnings.single())
    }

    @Test
    fun eachSkippedMeasurementIsWarnedAboutOnce() = runBlocking<Unit> {
        // The map loads the list again on every camera move.
        val orphan = TestRecords.measurement("latency", "g2").copy(id = "orphan", groupId = null)
        val unknown = TestRecords.measurement("upload", "g3").copy(id = "g3-jitter", type = "jitter")
        val rows = (test("g1") + orphan).map { it.asEntityWithData() }.toMutableList()
        val warnings = mutableListOf<String>()
        val repository = MeasurementRepository(FakeMeasurementDao(rows), FakeSubmissionDao(emptyList()), unusedDatasource()) {
            warnings += it
        }

        repository.getMeasurementGroups()
        repository.getMeasurementGroups()
        rows += unknown.asEntityWithData()
        repository.getMeasurementGroups()

        assertEquals(2, warnings.size)
        assertTrue(warnings[0], "orphan" in warnings[0])
        assertTrue(warnings[1], "g3-jitter" in warnings[1] && "orphan" !in warnings[1])
    }

    private fun test(groupId: String) =
        listOf("latency", "download", "upload").map { TestRecords.measurement(it, groupId) }

    private suspend fun groups(
        measurements: List<Measurement>,
        submissions: List<FccSubmission> = emptyList(),
        warn: (String) -> Unit = { throw AssertionError("unexpected warning: $it") },
    ) = MeasurementRepository(
        FakeMeasurementDao(measurements.map { it.asEntityWithData() }),
        FakeSubmissionDao(submissions.map { it.asEntity() }),
        unusedDatasource(),
        warn,
    ).getMeasurementGroups()

    // getMeasurementGroups never uses the network, but a real NetworkMeasurementDatasource needs a
    // LocalDataStore, which needs an Android Context. So make one without running its constructor.
    private fun unusedDatasource(): NetworkMeasurementDatasource {
        val unsafe = Class.forName("sun.misc.Unsafe")
        val theUnsafe = unsafe.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)
        return unsafe.getMethod("allocateInstance", Class::class.java)
            .invoke(theUnsafe, NetworkMeasurementDatasource::class.java) as NetworkMeasurementDatasource
    }

    // Room's DAOs are abstract classes. These implement the reads getMeasurementGroups makes.
    private class FakeMeasurementDao(private val rows: List<MeasurementWithData>) : MeasurementDao() {
        override suspend fun getMeasurementsWithData() = rows

        override suspend fun insertMeasurement(measurement: MeasurementEntity): Unit = unused()
        override suspend fun insertUploadDownloadData(uploadDownloadDataEntity: UploadDownloadDataEntity): Unit = unused()
        override suspend fun insertLatencyData(latencyDataEntity: LatencyDataEntity): Unit = unused()
        override suspend fun insertCell(cellEntity: CellEntity): Unit = unused()
        override suspend fun insertLocation(locationEntity: LocationEntity): Unit = unused()
        override suspend fun updateMeasurement(measurement: MeasurementEntity): Unit = unused()
        override suspend fun deleteMeasurement(measurement: MeasurementEntity): Unit = unused()
        override suspend fun deleteAllMeasurements(): Unit = unused()
        override suspend fun getMeasurementById(id: String): MeasurementEntity = unused()
        override suspend fun getMeasurementsInGroup(groupId: String): List<MeasurementEntity> = unused()
        override fun getMeasurementsFlow(): Flow<List<MeasurementEntity>> = unused()
        override suspend fun getUnsynchronizedMeasurementsWithData(): List<MeasurementWithData> = unused()
        override fun getMeasurementsWithDataFlow(): Flow<List<MeasurementWithData>> = unused()
        override suspend fun getMeasurementByIdWithData(id: String): MeasurementWithData = unused()
    }

    private class FakeSubmissionDao(private val rows: List<FccSubmissionEntity>) : FccSubmissionDao() {
        override suspend fun getFccSubmissions() = rows

        override suspend fun insertFccSubmission(fccSubmission: FccSubmissionEntity): Unit = unused()
        override suspend fun updateFccSubmission(fccSubmission: FccSubmissionEntity): Unit = unused()
        override suspend fun deleteFccSubmission(fccSubmission: FccSubmissionEntity): Unit = unused()
        override suspend fun deleteAllFccSubmissions(): Unit = unused()
        override suspend fun getFccSubmissionById(id: String): FccSubmissionEntity = unused()
        override suspend fun getFccSubmissionsWithMeasurements(): List<FccSubmissionWithMeasurements> = unused()
        override suspend fun getUnsynchronizedFccSubmissions(): List<FccSubmissionEntity> = unused()
        override fun getFccSubmissionsFlow(): Flow<List<FccSubmissionEntity>> = unused()
    }
}

private fun unused(): Nothing = throw UnsupportedOperationException("getMeasurementGroups doesn't use this")
