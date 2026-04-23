package edu.gatech.cc.cellwatch.domain.fcc

import edu.gatech.cc.cellwatch.domain.model.FccSubmission
import edu.gatech.cc.cellwatch.domain.model.LatencyData
import edu.gatech.cc.cellwatch.domain.model.Measurement
import edu.gatech.cc.cellwatch.domain.model.UploadDownloadData
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FccSubmissionValidationPolicyTest {
    @Test
    fun `validate allows complete submission`() {
        val result = FccSubmissionValidationPolicy.validate(
            submission = validSubmission(),
            latencyMeasurement = latencyMeasurement(),
            downloadMeasurement = throughputMeasurement("download"),
            uploadMeasurement = throughputMeasurement("upload"),
        )

        assertTrue(result.allowed)
        assertTrue(result.codes.isEmpty())
    }

    @Test
    fun `validate rejects missing app identity fields`() {
        val result = FccSubmissionValidationPolicy.validate(
            submission = validSubmission().copy(
                appName = "",
                appVersion = null,
                deviceId = "   ",
            ),
            latencyMeasurement = latencyMeasurement(),
            downloadMeasurement = throughputMeasurement("download"),
            uploadMeasurement = throughputMeasurement("upload"),
        )

        assertFalse(result.allowed)
        assertEquals(
            setOf(
                FccSubmissionValidationCode.MISSING_APP_NAME,
                FccSubmissionValidationCode.MISSING_APP_VERSION,
                FccSubmissionValidationCode.MISSING_DEVICE_ID,
            ),
            result.codes.intersect(
                setOf(
                    FccSubmissionValidationCode.MISSING_APP_NAME,
                    FccSubmissionValidationCode.MISSING_APP_VERSION,
                    FccSubmissionValidationCode.MISSING_DEVICE_ID,
                ),
            ),
        )
    }

    @Test
    fun `validate rejects malformed contact phone for challenge submission`() {
        val result = FccSubmissionValidationPolicy.validate(
            submission = validSubmission().copy(contactPhone = "555-0100"),
            latencyMeasurement = latencyMeasurement(),
            downloadMeasurement = throughputMeasurement("download"),
            uploadMeasurement = throughputMeasurement("upload"),
        )

        assertFalse(result.allowed)
        assertTrue(result.codes.contains(FccSubmissionValidationCode.INVALID_CONTACT_PHONE))
    }

    @Test
    fun `validate does not require ios exempt carrier network codes`() {
        val result = FccSubmissionValidationPolicy.validate(
            submission = validSubmission().copy(
                deviceType = "iOS",
                provider = null,
                simCountryCode = null,
                simNetworkCode = null,
                netCountryCode = null,
                netNetworkCode = null,
            ),
            latencyMeasurement = latencyMeasurement(),
            downloadMeasurement = throughputMeasurement("download"),
            uploadMeasurement = throughputMeasurement("upload"),
        )

        assertTrue(result.allowed)
    }

    private fun validSubmission(): FccSubmission {
        return FccSubmission(
            id = "group-1",
            appName = "CellWatch",
            appVersion = "1.0 (1)",
            deviceId = "device-1",
            deviceType = "Android",
            deviceManufacturer = "Google",
            deviceModel = "Pixel",
            deviceOsName = "Android 14",
            provider = "Carrier A",
            contactName = "Ada Lovelace",
            contactEmail = "ada@example.com",
            contactPhone = "404-111-2222",
        )
    }

    private fun latencyMeasurement(): Measurement {
        return Measurement(
            id = "latency-1",
            groupId = "group-1",
            type = "latency",
            latencyData = LatencyData(
                id = "latency-data",
                measurementId = "latency-1",
                rtt = 10,
            ),
        )
    }

    private fun throughputMeasurement(type: String): Measurement {
        return Measurement(
            id = "$type-1",
            groupId = "group-1",
            type = type,
            uploadDownloadData = UploadDownloadData(
                id = "ud-$type",
                measurementId = "$type-1",
                bytes = 100,
            ),
        )
    }
}
