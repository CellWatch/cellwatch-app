package edu.gatech.cc.cellwatch

import com.google.gson.GsonBuilder
import edu.gatech.cc.cellwatch.core.util.Log
import edu.gatech.cc.cellwatch.data.model.Cell
import edu.gatech.cc.cellwatch.data.model.Location
import edu.gatech.cc.cellwatch.data.model.Measurement
import edu.gatech.cc.cellwatch.data.model.UploadDownloadData
import edu.gatech.cc.cellwatch.data.model.asNetworkModelWithData
import edu.gatech.cc.cellwatch.domain.telephony.managers.NetworkConnectionType
import kotlinx.datetime.Clock
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.UUID

class NetworkMeasurementWithDataTest {
    @Test
    fun SerializeMeasurementToNetworkMeasurementWithData() {
        val gson = GsonBuilder().setPrettyPrinting().create()
        val deviceId = UUID.randomUUID().toString()
        val groupId = UUID.randomUUID().toString()
        val campaignId = UUID.randomUUID().toString()
        val sessionId = UUID.randomUUID().toString()

        val downloadLocations = listOf(
            Location(
                timestamp = Clock.System.now(),
                lat = 33.87297,
                lon = -84.3413,
                accuracy = 20.883,
                speed = .323,
                heading = 234.98
            ),
            Location(
                timestamp = Clock.System.now(),
                lat = 33.87797,
                lon = -84.3111,
                accuracy = 20.3,
                speed = .123,
                heading = 234.23
            ),
            Location(
                timestamp = Clock.System.now(),
                lat = 33.5797,
                lon = -84.6411,
                accuracy = 18.55,
                speed = .623,
                heading = 204.663
            )
        )

        val downloadData = UploadDownloadData(
            id = UUID.randomUUID().toString(),
            warmupDuration = 12344,
            warmupBytes = 56325,
            duration = 3756444,
            bytes = 53724,
            servers = listOf("server1", "server2")
        )

        val downloadCells = listOf(
            Cell(
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
            Cell(
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

        val downloadMeasurement = Measurement(
            id = UUID.randomUUID().toString(),
            groupId = groupId,
            campaignId = campaignId,
            sessionId = sessionId,
            deviceId = deviceId,
            deviceManufacturer = "Google",
            deviceModel = "Pixel 5",
            deviceOsName = "Android",
            deviceOsVersion = "13",
            appName = "CellWatch",
            provider = "T-Mobile",
            type = "download", //direction.toString().lowercase(),
            timestamp = Clock.System.now(),
            duration = 7288314,
            scheduled = false,
            success = true,
            carrierAggregation = false,
            networkAvailable = true,
            networkConnected = true,
            networkRoaming = false,
            extraData = "extraData",
            uploadDownloadData = downloadData,
            locations = downloadLocations,
            cells = downloadCells,
            cellularDataEnabled = true,
            connectionType = NetworkConnectionType.CELLULAR,
        )
        val networkDownloadMeasurement = downloadMeasurement.asNetworkModelWithData()

        assertEquals(networkDownloadMeasurement.cells?.count(), 2)

        val jsonString = gson.toJson(networkDownloadMeasurement)
        Log.d(NetworkMeasurementSubmissionTest.TAG, jsonString)
    }

}
