package com.cellwatch

import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cellwatch.data.local.CellWatchDatabase
import com.cellwatch.data.local.dao.CellDao
import com.cellwatch.data.local.model.LatencyDataEntity
import com.cellwatch.data.local.model.LocationEntity
import com.cellwatch.data.local.model.MeasurementEntity
import com.cellwatch.data.local.model.MeasurementWithData
import com.cellwatch.data.local.model.UploadDownloadDataEntity
import com.cellwatch.data.local.dao.LatencyDataDao
import com.cellwatch.data.local.dao.LocationDao
import com.cellwatch.data.local.dao.MeasurementDao
import com.cellwatch.data.local.dao.UploadDownloadDataDao
import com.cellwatch.data.local.model.CellEntity
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class MeasurementDaoTest {
    private lateinit var db: CellWatchDatabase
    private lateinit var measurementDao: MeasurementDao
    private lateinit var locationDao: LocationDao
    private lateinit var dataDao: UploadDownloadDataDao
    private lateinit var latencyDao: LatencyDataDao
    private lateinit var cellDao: CellDao

    companion object {
        private const val TAG = "MeasurementDaoTest"
    }
    
    @Before
    fun createDb() {
        val context: Context = ApplicationProvider.getApplicationContext()
        // Using an in-memory database because the information stored here disappears when the
        // process is killed.
        db = Room.inMemoryDatabaseBuilder(context, CellWatchDatabase::class.java)
            // Allowing main thread queries, just for testing.
            .allowMainThreadQueries()
            .build()
        measurementDao = db.measurementDao()
        locationDao = db.locationDao()
        dataDao = db.uploadDownloadDataDao()
        latencyDao = db.latencyDataDao()
        cellDao = db.cellDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    @Throws(Exception::class)
    fun convertJson() {
        val gson = Gson()
        val gsonPretty = GsonBuilder().setPrettyPrinting().create()
        val deviceId = UUID.randomUUID().toString()
        val measurement = MeasurementEntity(
            deviceId = deviceId,
            deviceManufacturer = "Samsung",
            appName = "CellWatch",
            type = "download", //direction.toString().lowercase(),
            duration = 7288314,
            scheduled = false,
            success = true,
            carrierAggregation = false,
            networkAvailable = true,
            networkConnected = true,
            networkRoaming = false
        )
        val measurementJson: String = gson.toJson(measurement)
        Log.d("CellWatchTest","**** Measurement JSON = $measurementJson")
    }

    @Test
    @Throws(Exception::class)
    fun insertAndGetMeasurement() {
//        val gson = Gson()
        val gsonPretty = GsonBuilder().setPrettyPrinting().create()
        val deviceId = UUID.randomUUID().toString()
        val groupId = UUID.randomUUID().toString()
        val measurement = MeasurementEntity(
            groupId = groupId,
            deviceId = deviceId,
            deviceManufacturer = "Samsung",
            appName = "CellWatch",
            type = "download", //direction.toString().lowercase(),
            duration = 7288314,
            scheduled = false,
            success = true,
            carrierAggregation = false,
            networkAvailable = true,
            networkConnected = true,
            networkRoaming = false,
            simMobileCountryCode = "310",
            simMobileNetworkCode = "310",
            netMobileCountryCode = "410",
            netMobileNetworkCode = "410"
        )
        val measurementJson: String = gsonPretty.toJson(measurement)
        println("**** Measurement JSON = $measurementJson")


        runBlocking { measurementDao.insertMeasurement(measurement) }
        val allMeasurements = runBlocking { measurementDao.getMeasurementsFlow().first() }
        assertEquals(allMeasurements[0].id, measurement.id)
        assertEquals(allMeasurements[0].deviceId, measurement.deviceId)
        assertEquals(allMeasurements[0].appName, measurement.appName)
        assertEquals(allMeasurements[0].deviceManufacturer, measurement.deviceManufacturer)
        assertEquals(allMeasurements[0].deviceModel, measurement.deviceModel)
        assertEquals(allMeasurements[0].type, "download")
        assertEquals(allMeasurements[0].simMobileCountryCode, "310")
        assertEquals(allMeasurements[0].simMobileCountryCode, "310")

        print("******** Done with insertAndGetMeasurement")
//        Log.d(TAG, "insertAndGetMeasurement")
    }

    @Test
    @Throws(Exception::class)
    fun getAllMeasurements() {
        val deviceId = UUID.randomUUID().toString()
        val groupId = UUID.randomUUID().toString()

        val downloadMeasurement = MeasurementEntity(
            groupId = groupId,
            deviceId = deviceId,
            type = "download",
            appName = "CellWatch",
            deviceManufacturer = "Samsung",
            deviceModel = "Galaxy",
            networkRoaming = false,
            simMobileCountryCode = "310",
            simMobileNetworkCode = "410",
            netMobileCountryCode = "310",
            netMobileNetworkCode = "410"
        )
        val uploadMeasurement = MeasurementEntity(
            groupId = groupId,
            deviceId = deviceId,
            type = "upload",
            appName = "CellWatch",
            deviceManufacturer = "Google",
            deviceModel = "Pixel",
            networkRoaming = false,
            simMobileCountryCode = "310",
            simMobileNetworkCode = "410",
            netMobileCountryCode = "310",
            netMobileNetworkCode = "410"
        )
        val latencyMeasurement = MeasurementEntity(
            groupId = groupId,
            deviceId = deviceId,
            type = "latency",
            appName = "CellWatch",
            deviceManufacturer = "Google",
            deviceModel = "Pixel"
        )
        val locationEntity = LocationEntity(
//            measurementId = downloadMeasurement.id,
            timestamp = Clock.System.now(),
            lat = 33.5597,
            lon = -84.9011,
            accuracy = 18.15,
            speed = .523,
            heading = 214.663
        )
        val locationEntity2 = LocationEntity(
//            measurementId = downloadMeasurement.id,
            timestamp = Clock.System.now(),
            lat = 33.5597,
            lon = -84.9011,
            accuracy = 18.15,
            speed = .523,
            heading = 214.663
        )
        val locationEntity3 = LocationEntity(
//            measurementId = downloadMeasurement.id,
            timestamp = Clock.System.now(),
            lat = 33.5597,
            lon = -84.9011,
            accuracy = 18.15,
            speed = .523,
            heading = 214.663
        )
        val downloadData = UploadDownloadDataEntity(
//            measurementId = downloadMeasurement.id,
            warmupDuration = 12344,
            warmupBytes = 56325,
            duration = 3756444,
            bytes = 53724,
            applicationBytes = 56724,
            servers = listOf("server1", "server2")
        )
        val uploadData = UploadDownloadDataEntity(
//            measurementId = downloadMeasurement.id,
            warmupDuration = 10234,
            warmupBytes = 134425,
            duration = 9372444,
            bytes = 83724,
            applicationBytes = 56724,
            servers = listOf("server1", "server2")
        )
        val latencyDataEntity = LatencyDataEntity(
//            measurementId = downloadMeasurement.id,
            rtt = 12355,
            jitter = 88372,
            sent = 8124553,
            received = 779927,
            servers = listOf("server1", "server2")
        )
        val downloadCells = listOf<CellEntity>(
            CellEntity(
                timestamp = Clock.System.now(),
                cellId = 234,
                physicalCellId = 4321,
                cellConnection = 1,
                networkGeneration = "5G",
                networkSubtype = "GSM",
                signalStrength = -103,
                rssi = -78,
                rsrp = -102,
                rsrq = -12,
                sinr = 2,
                csiRsrp = -101,
                csiRsrq = -13,
                csiSinr = 2,
                cqi = 4,
                spectrumBand = "n41",
                spectrumBandwidth = 100.0f,
                arfcn = 528000
            ),
            CellEntity(
                timestamp = Clock.System.now(),
                cellId = 235,
                physicalCellId = 4322,
                cellConnection = 1,
                networkGeneration = "5G",
                networkSubtype = "GSM",
                signalStrength = -102,
                rssi = -79,
                rsrp = -101,
                rsrq = -11,
                sinr = 2,
                csiRsrp = -100,
                csiRsrq = -14,
                csiSinr = 2,
                cqi = 4,
                spectrumBand = "n41",
                spectrumBandwidth = 100.0f,
                arfcn = 528000
            )
        )
        val uploadCells = listOf<CellEntity>(
            CellEntity(
                timestamp = Clock.System.now(),
                cellId = 234,
                physicalCellId = 4321,
                cellConnection = 1,
                networkGeneration = "5G",
                networkSubtype = "GSM",
                signalStrength = -103,
                rssi = -78,
                rsrp = -102,
                rsrq = -12,
                sinr = 2,
                csiRsrp = -101,
                csiRsrq = -13,
                csiSinr = 2,
                cqi = 4,
                spectrumBand = "n41",
                spectrumBandwidth = 100.0f,
                arfcn = 528000
            ),
            CellEntity(
                timestamp = Clock.System.now(),
                cellId = 235,
                physicalCellId = 4322,
                cellConnection = 1,
                networkGeneration = "5G",
                networkSubtype = "GSM",
                signalStrength = -102,
                rssi = -79,
                rsrp = -101,
                rsrq = -11,
                sinr = 2,
                csiRsrp = -100,
                csiRsrq = -14,
                csiSinr = 2,
                cqi = 4,
                spectrumBand = "n41",
                spectrumBandwidth = 100.0f,
                arfcn = 528000
            )
        )
        val latencyCells = listOf<CellEntity>(
            CellEntity(
                timestamp = Clock.System.now(),
                cellId = 234,
                physicalCellId = 4321,
                cellConnection = 1,
                networkGeneration = "5G",
                networkSubtype = "GSM",
                signalStrength = -103,
                rssi = -78,
                rsrp = -102,
                rsrq = -12,
                sinr = 2,
                csiRsrp = -101,
                csiRsrq = -13,
                csiSinr = 2,
                cqi = 4,
                spectrumBand = "n41",
                spectrumBandwidth = 100.0f,
                arfcn = 528000
            ),
            CellEntity(
                timestamp = Clock.System.now(),
                cellId = 235,
                physicalCellId = 4322,
                cellConnection = 1,
                networkGeneration = "5G",
                networkSubtype = "GSM",
                signalStrength = -102,
                rssi = -79,
                rsrp = -101,
                rsrq = -11,
                sinr = 2,
                csiRsrp = -100,
                csiRsrq = -14,
                csiSinr = 2,
                cqi = 4,
                spectrumBand = "n41",
                spectrumBandwidth = 100.0f,
                arfcn = 528000
            )
        )

        runBlocking {

//            measurementDao.insertMeasurement(measurement)
//            latencyDao.insertLatencyData(latencyData)
//            dataDao.insertUploadDownloadData(downloadData)
//            dataDao.insertUploadDownloadData(uploadData)

            val downloadMeasurementWithData = MeasurementWithData(
                downloadMeasurement,
                downloadData,
                null,
                listOf<LocationEntity>(locationEntity),
                downloadCells
            )
            measurementDao.insertMeasurementWithData(
                downloadMeasurementWithData
            )
            val uploadMeasurementWithData = MeasurementWithData(
                uploadMeasurement,
                uploadData,
                null,
                listOf<LocationEntity>(locationEntity2),
                uploadCells
            )
            measurementDao.insertMeasurementWithData(
                uploadMeasurementWithData
            )
            val latencyMeasurementWithData = MeasurementWithData(
                latencyMeasurement,
                null,
                latencyDataEntity,
                listOf<LocationEntity>(locationEntity3),
                latencyCells
            )
            measurementDao.insertMeasurementWithData(
                latencyMeasurementWithData
            )

//            measurementDao.insertMeasurement(uploadMeasurement)

            val allMeasurements = measurementDao.getUnsynchronizedMeasurementsWithData()
//            val allMeasurements = measurementDao.getMeasurementsWithDataFlow().first()
            assertEquals(allMeasurements[0].measurement.id, downloadMeasurement.id)
            assertEquals(allMeasurements[1].measurement.id, uploadMeasurement.id)
            assertEquals(allMeasurements[0].measurement.simMobileNetworkCode, "410")
            with(allMeasurements[0]) {
                assertEquals(measurement.type, "download")
                assertEquals(measurement.simMobileNetworkCode, "410")
                Log.d(TAG, "measurement.simMobileNetworkCode = ${measurement.simMobileNetworkCode}")
                uploadDownloadData?.let {
                    assertEquals(it.measurementId, measurement.id)
                    assertEquals(it.bytes, 53724L)
                    assertEquals(it.applicationBytes, 56724L)
                }
                locations?.forEach {
                    assertEquals(it.lat, 33.5597)
                }
                cells?.forEach {
                    assertEquals(it.arfcn, 528000)
                    Log.d(TAG, "cell = ${it}")
                }
            }

//            val allLatencyData = latencyDao.getLatencyDataByMeasurementIdFlow(downloadMeasurement.id).first()
//            assertEquals(allLatencyData[0].id, latencyData.id)
//            assertEquals(allLatencyData[0].measurementId, downloadMeasurement.id)

//            val allData = dataDao.getUploadDownloadDataByMeasurementIdFlow(downloadMeasurement.id).first()
//            assertEquals(allData[0].id, downloadData.id)
//            assertEquals(allData[1].id, uploadData.id)
//            assertEquals(allData[0].measurementId, downloadMeasurement.id)
//            assertEquals(allData[1].measurementId, downloadMeasurement.id)

//            val measurementWithAllData: List<MeasurementWithAllData> =
//                measurementDao.getMeasurementsWithAllDataFlow().first()
//            assertEquals(measurementWithAllData[0].uploadDownloadData[0].measurementId,
//                measurement.id)

            Log.d(TAG, "Got Measurement.id of ${allMeasurements[0].measurement.id}")
        }
    }

    @Test
    @Throws(Exception::class)
    fun deleteAll() {
        runBlocking {
            val measurement = MeasurementEntity(
                type = "download",
                appName = "CellWatch",
                deviceManufacturer = "Samsung",
                deviceModel = "Galaxy",
                deviceId = "myID00"
            )
            measurementDao.insertMeasurement(measurement)
            val measurement2 = MeasurementEntity(
                type = "upload",
                appName = "CellWatch",
                deviceManufacturer = "Google",
                deviceModel = "Pixel",
                deviceId = "myID01"
            )
            measurementDao.insertMeasurement(measurement2)
            measurementDao.deleteAllMeasurements()
            val allMeasurements = measurementDao.getMeasurementsFlow().first()
            Assert.assertTrue(allMeasurements.isEmpty())
        }
    }
}