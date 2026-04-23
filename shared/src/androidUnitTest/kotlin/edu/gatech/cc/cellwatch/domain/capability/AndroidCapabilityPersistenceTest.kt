package edu.gatech.cc.cellwatch.domain.capability

import android.Manifest
import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import edu.gatech.cc.cellwatch.data.repo.CellRepositoryImpl
import edu.gatech.cc.cellwatch.data.repo.LatencyDataRepositoryImpl
import edu.gatech.cc.cellwatch.data.repo.LocationRepositoryImpl
import edu.gatech.cc.cellwatch.data.repo.MeasurementRepositoryImpl
import edu.gatech.cc.cellwatch.data.repo.UploadDownloadDataRepositoryImpl
import edu.gatech.cc.cellwatch.db.CellwatchDatabase
import edu.gatech.cc.cellwatch.domain.fcc.RepositoryBackedMeasurementResultStore
import edu.gatech.cc.cellwatch.domain.model.Measurement
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import kotlin.coroutines.EmptyCoroutineContext

@RunWith(RobolectricTestRunner::class)
class AndroidCapabilityPersistenceTest {

    private lateinit var driver: AndroidSqliteDriver
    private lateinit var db: CellwatchDatabase
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        shadowOf(context as Application).grantPermissions(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
        )
        driver = AndroidSqliteDriver(CellwatchDatabase.Schema, context, null)
        driver.execute(null, "PRAGMA foreign_keys=ON", 0) {}
        db = CellwatchDatabase(driver)
        AndroidLocationSampleBridge.clearSample()
    }

    @After
    fun tearDown() {
        AndroidLocationSampleBridge.clearSample()
        if (this::driver.isInitialized) {
            driver.close()
        }
    }

    @Test
    fun bridgeLocationSample_persistsIntoStoredMeasurementRows() = runBlocking {
        AndroidLocationSampleBridge.updateSample(
            AndroidLocationSample(
                timestamp = Instant.fromEpochMilliseconds(1_710_000_111_000L),
                lat = 33.7492,
                lon = -84.3883,
                accuracy = 4.5,
                speed = 0.8,
                speedAccuracy = 0.2,
                heading = 181.0,
            ),
        )

        val snapshot = AndroidPlatformCapabilityProvider(context).captureSnapshot()
        val enriched = MeasurementCapabilityEnricher().enrich(
            measurement = Measurement(
                id = "android-bridge-measurement",
                groupId = "group-android-bridge",
                type = "latency",
            ),
            snapshot = snapshot,
        )

        val measurementRepo = MeasurementRepositoryImpl(db.measurementQueries, EmptyCoroutineContext)
        val latencyRepo = LatencyDataRepositoryImpl(db.latencyDataQueries, EmptyCoroutineContext)
        val uploadRepo = UploadDownloadDataRepositoryImpl(db.uploadDownloadDataQueries, EmptyCoroutineContext)
        val locationRepo = LocationRepositoryImpl(db.locationQueries, EmptyCoroutineContext)
        val cellRepo = CellRepositoryImpl(db.cellQueries, EmptyCoroutineContext)
        val store = RepositoryBackedMeasurementResultStore(
            measurementRepository = measurementRepo,
            latencyDataRepository = latencyRepo,
            uploadDownloadDataRepository = uploadRepo,
            submissionRepository = edu.gatech.cc.cellwatch.data.repo.FccSubmissionRepositoryImpl(
                db.fccSubmissionQueries,
                EmptyCoroutineContext,
            ),
            locationRepository = locationRepo,
            cellRepository = cellRepo,
        )

        store.insertMeasurement(enriched)

        val storedMeasurement = measurementRepo.getById("android-bridge-measurement")
        val storedLocations = locationRepo.getByMeasurementId("android-bridge-measurement")

        assertEquals("PARTIAL", storedMeasurement?.locationSupport)
        assertTrue(storedMeasurement?.capabilityNotes?.contains("active location sample") == true)
        assertEquals(1, storedLocations.size)
        assertEquals(33.7492, storedLocations.first().lat, 0.000001)
        assertEquals(-84.3883, storedLocations.first().lon, 0.000001)
        assertEquals(4.5, storedLocations.first().accuracy!!, 0.000001)
    }
}
