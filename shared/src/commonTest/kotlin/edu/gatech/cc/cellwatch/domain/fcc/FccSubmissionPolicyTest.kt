package edu.gatech.cc.cellwatch.domain.fcc

import edu.gatech.cc.cellwatch.domain.model.CollectionMode
import edu.gatech.cc.cellwatch.domain.model.Measurement
import edu.gatech.cc.cellwatch.domain.model.NetworkConnectionType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FccSubmissionPolicyTest {
    @Test
    fun `shouldCreateSubmission is false for non challenge mode`() {
        val latency = measurement("latency", connectionType = NetworkConnectionType.CELLULAR, cellularDataEnabled = true)
        val download = measurement("download", connectionType = NetworkConnectionType.CELLULAR, cellularDataEnabled = true)
        val upload = measurement("upload", connectionType = NetworkConnectionType.CELLULAR, cellularDataEnabled = true)

        val actual = FccSubmissionPolicy.shouldCreateSubmission(
            mode = CollectionMode.TESTING,
            latencyMeasurement = latency,
            downloadMeasurement = download,
            uploadMeasurement = upload,
        )

        assertFalse(actual)
    }

    @Test
    fun `shouldCreateSubmission is false when any measurement is wifi`() {
        val latency = measurement("latency", connectionType = NetworkConnectionType.WIFI, cellularDataEnabled = true)
        val download = measurement("download", connectionType = NetworkConnectionType.CELLULAR, cellularDataEnabled = true)
        val upload = measurement("upload", connectionType = NetworkConnectionType.CELLULAR, cellularDataEnabled = true)

        val actual = FccSubmissionPolicy.shouldCreateSubmission(
            mode = CollectionMode.FCC_CHALLENGE,
            latencyMeasurement = latency,
            downloadMeasurement = download,
            uploadMeasurement = upload,
        )

        assertFalse(actual)
    }

    @Test
    fun `shouldCreateSubmission is false when any measurement has cellular disabled`() {
        val latency = measurement("latency", connectionType = NetworkConnectionType.CELLULAR, cellularDataEnabled = false)
        val download = measurement("download", connectionType = NetworkConnectionType.CELLULAR, cellularDataEnabled = true)
        val upload = measurement("upload", connectionType = NetworkConnectionType.CELLULAR, cellularDataEnabled = true)

        val actual = FccSubmissionPolicy.shouldCreateSubmission(
            mode = CollectionMode.FCC_CHALLENGE,
            latencyMeasurement = latency,
            downloadMeasurement = download,
            uploadMeasurement = upload,
        )

        assertFalse(actual)
    }

    @Test
    fun `shouldCreateSubmission is true when all are non wifi and cellular is not false`() {
        val latency = measurement("latency", connectionType = NetworkConnectionType.CELLULAR, cellularDataEnabled = null)
        val download = measurement("download", connectionType = NetworkConnectionType.CELLULAR, cellularDataEnabled = true)
        val upload = measurement("upload", connectionType = NetworkConnectionType.NONE, cellularDataEnabled = true)

        val actual = FccSubmissionPolicy.shouldCreateSubmission(
            mode = CollectionMode.FCC_CHALLENGE,
            latencyMeasurement = latency,
            downloadMeasurement = download,
            uploadMeasurement = upload,
        )

        assertTrue(actual)
    }

    @Test
    fun `metadataSnapshot uses latency first then download then upload fallback`() {
        val latency = measurement(
            type = "latency",
            deviceId = null,
            deviceManufacturer = "latency-manufacturer",
            deviceModel = null,
            deviceOsVersion = "14",
            appName = null,
            simMcc = "310",
            simMnc = null,
            netMcc = null,
            netMnc = "410",
        )
        val download = measurement(
            type = "download",
            deviceId = "download-device",
            deviceManufacturer = null,
            deviceModel = "download-model",
            deviceOsVersion = null,
            appName = "download-app",
            simMcc = null,
            simMnc = "260",
            netMcc = "311",
            netMnc = null,
        )
        val upload = measurement(
            type = "upload",
            deviceId = "upload-device",
            deviceManufacturer = "upload-manufacturer",
            deviceModel = "upload-model",
            deviceOsVersion = "15",
            appName = "upload-app",
            simMcc = "999",
            simMnc = "999",
            netMcc = "999",
            netMnc = "999",
        )

        val actual = FccSubmissionPolicy.metadataSnapshot(
            latencyMeasurement = latency,
            downloadMeasurement = download,
            uploadMeasurement = upload,
        )

        assertEquals("download-device", actual.deviceId)
        assertEquals("latency-manufacturer", actual.deviceManufacturer)
        assertEquals("download-model", actual.deviceModel)
        assertEquals("14", actual.deviceOsVersion)
        assertEquals("download-app", actual.appName)
        assertEquals("310", actual.simMcc)
        assertEquals("260", actual.simMnc)
        assertEquals("311", actual.netMcc)
        assertEquals("410", actual.netMnc)
    }

    private fun measurement(
        type: String,
        connectionType: NetworkConnectionType = NetworkConnectionType.CELLULAR,
        cellularDataEnabled: Boolean? = true,
        deviceId: String? = null,
        deviceManufacturer: String? = null,
        deviceModel: String? = null,
        deviceOsVersion: String? = null,
        appName: String? = null,
        simMcc: String? = null,
        simMnc: String? = null,
        netMcc: String? = null,
        netMnc: String? = null,
    ): Measurement {
        return Measurement(
            type = type,
            connectionType = connectionType,
            cellularDataEnabled = cellularDataEnabled,
            deviceId = deviceId,
            deviceManufacturer = deviceManufacturer,
            deviceModel = deviceModel,
            deviceOsVersion = deviceOsVersion,
            appName = appName,
            simMcc = simMcc,
            simMnc = simMnc,
            netMcc = netMcc,
            netMnc = netMnc,
        )
    }
}
