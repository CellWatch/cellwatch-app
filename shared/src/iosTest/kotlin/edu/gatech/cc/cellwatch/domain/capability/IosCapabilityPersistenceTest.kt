package edu.gatech.cc.cellwatch.domain.capability

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.benasher44.uuid.uuid4
import edu.gatech.cc.cellwatch.data.repo.CellRepositoryImpl
import edu.gatech.cc.cellwatch.data.repo.FccSubmissionRepositoryImpl
import edu.gatech.cc.cellwatch.data.repo.LatencyDataRepositoryImpl
import edu.gatech.cc.cellwatch.data.repo.LocationRepositoryImpl
import edu.gatech.cc.cellwatch.data.repo.MeasurementRepositoryImpl
import edu.gatech.cc.cellwatch.data.repo.UploadDownloadDataRepositoryImpl
import edu.gatech.cc.cellwatch.db.CellwatchDatabase
import edu.gatech.cc.cellwatch.domain.fcc.RepositoryBackedMeasurementResultStore
import edu.gatech.cc.cellwatch.domain.model.Location
import edu.gatech.cc.cellwatch.domain.model.Measurement
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class IosCapabilityPersistenceTest {

    private lateinit var driver: NativeSqliteDriver
    private lateinit var db: CellwatchDatabase

    @BeforeTest
    fun setUp() {
        driver = NativeSqliteDriver(CellwatchDatabase.Schema, "ios-capability-persistence-${uuid4()}.db")
        driver.execute(null, "PRAGMA foreign_keys=ON", 0) {}
        db = CellwatchDatabase(driver)
        IosLocationSampleBridge.clearSample()
    }

    @AfterTest
    fun tearDown() {
        IosLocationSampleBridge.clearSample()
        if (this::driver.isInitialized) {
            driver.close()
        }
    }

    @Test
    fun bridgeLocationSample_persistsIntoStoredMeasurementRows() = runBlocking {
        IosLocationSampleBridge.updateSample(
            timestampEpochMillis = 1_710_000_222_000L,
            lat = 33.7499,
            lon = -84.3877,
            accuracy = 6.0,
            speed = 0.4,
            speedAccuracy = 0.1,
            heading = 92.0,
        )
        val sample = assertNotNull(IosLocationSampleBridge.currentSample())

        val snapshot = PlatformCapabilitySnapshot(
            telephony = TelephonyCapabilitySnapshot(
                support = CapabilitySupport.PARTIAL,
                networkGeneration = "5G",
                networkSubtype = "NR",
                note = "best-effort iOS radio access technology from CoreTelephony; carrier, MCC/MNC, and cell detail remain unavailable",
            ),
            network = NetworkCapabilitySnapshot(
                support = CapabilitySupport.PARTIAL,
                note = "iOS network capability adapter currently provides best-effort partial data only",
            ),
            location = LocationCapabilitySnapshot(
                support = CapabilitySupport.PARTIAL,
                samples = listOf(
                    Location(
                        timestamp = sample.timestamp,
                        lat = sample.lat,
                        lon = sample.lon,
                        accuracy = sample.accuracy,
                        speed = sample.speed,
                        speedAccuracy = sample.speedAccuracy,
                        heading = sample.heading,
                    ),
                ),
                note = "best-effort iOS location snapshot from active CLLocationManager delegate updates",
            ),
            device = DeviceCapabilitySnapshot(
                support = CapabilitySupport.AVAILABLE,
                manufacturer = "Apple",
                model = "iPhone",
                osName = "iOS",
                osVersion = "18",
                appVersion = "test-ios-app",
                note = "best-effort iOS device metadata snapshot",
            ),
        )

        val enriched = MeasurementCapabilityEnricher().enrich(
            measurement = Measurement(
                id = "ios-bridge-measurement",
                groupId = "group-ios-bridge",
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
            submissionRepository = FccSubmissionRepositoryImpl(db.fccSubmissionQueries, EmptyCoroutineContext),
            locationRepository = locationRepo,
            cellRepository = cellRepo,
        )

        store.insertMeasurement(enriched)

        val storedMeasurement = measurementRepo.getById("ios-bridge-measurement")
        val storedLocations = locationRepo.getByMeasurementId("ios-bridge-measurement")

        assertEquals("PARTIAL", storedMeasurement?.locationSupport)
        assertEquals("PARTIAL", storedMeasurement?.telephonySupport)
        assertTrue(storedMeasurement?.capabilityNotes?.contains("active CLLocationManager delegate updates") == true)
        assertEquals(1, storedLocations.size)
        assertEquals(33.7499, storedLocations.first().lat)
        assertEquals(-84.3877, storedLocations.first().lon)
        assertEquals(6.0, storedLocations.first().accuracy)
    }
}
