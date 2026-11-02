package edu.gatech.cc.cellwatch.data.model

import edu.gatech.cc.cellwatch.domain.telephony.managers.NetworkConnectionType
import edu.gatech.cc.cellwatch.ui.main.Contact
import edu.gatech.cc.cellwatch.ui.main.FccSubmissionExportBundle
import kotlinx.datetime.Instant
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import java.util.Locale

/**
 * Checks the FCC export that the history screen writes (MeasureHistoryViewModel.exportData) against
 * the FCC's "Broadband Data Collection: Data Specifications for Mobile Speed Test Data" (July 28,
 * 2025), section 5.1. Section numbers refer to that document; [BdcSpec] has its field tables.
 *
 * Tests named "...AsReleased" pin places where the export differs from the spec, or from what the
 * server sends the FCC (private.fcc_submission_payload in supabase/full_schema.sql). Changing the
 * export changes the FCC data in it, so, like the test_id change in FccExportExtensionsTest, those
 * changes go on measurement-process-changes.
 */
class FccExportSpecTest {
    // The settings exportData writes the file with.
    @OptIn(ExperimentalSerializationApi::class)
    private val exportJson = Json {
        encodeDefaults = true
        explicitNulls = true
    }

    private val defaultLocale = Locale.getDefault()

    // The export formats coordinates with String.format, which follows the default locale.
    @Before
    fun useUsEnglish() {
        Locale.setDefault(Locale.US)
    }

    @After
    fun restoreLocale() {
        Locale.setDefault(defaultLocale)
    }

    @Test
    fun fileAndContactUseTheSpecsFieldNames() {
        val file = exportJson.encodeToJsonElement(
            FccSubmissionExportBundle(
                contact = Contact(name = "Jane Broadband", email = "jane.broadband@fcc.gov", phone = "888-225-5322"),
                submission_category = "Consumer Challenge",
                submissions = listOf(TestRecords.group().toFccSubmissionExport()!!),
            )
        ).jsonObject

        assertEquals("§5.1", BdcSpec.FILE, file.keys)
        assertEquals("§5.1.1", BdcSpec.CONTACT, file.obj("contact").keys)
    }

    @Test
    fun submissionUsesTheSpecsFieldNames() {
        val submission = export()

        assertEquals("§5.1.2", BdcSpec.SUBMISSION, submission.keys)
        assertEquals("§5.1.3", BdcSpec.TEST, submission.obj("tests").keys)
    }

    @Test
    fun downloadAndUploadUseTheSpecsFieldNames() {
        val tests = export().obj("tests")

        assertEquals("§5.1.4", BdcSpec.THROUGHPUT_TEST, tests.obj("download").keys)
        assertEquals("§5.1.5", BdcSpec.THROUGHPUT_TEST, tests.obj("upload").keys)
    }

    @Test
    fun latencyUsesTheSpecsFieldNames() {
        assertEquals("§5.1.6", BdcSpec.LATENCY_TEST, export().obj("tests").obj("latency").keys)
    }

    @Test
    fun locationsAndCellsUseTheSpecsFieldNames() {
        for ((type, test) in export().obj("tests")) {
            val locations = test.jsonObject.array("locations")
            val cells = test.jsonObject.array("cells")
            assertEquals(2, locations.size)
            assertEquals(2, cells.size)

            for (location in locations) {
                assertEquals("§5.1.7, $type", BdcSpec.LOCATION, location.jsonObject.keys)
            }
            for (cell in cells) {
                assertEquals("§5.1.8.1, $type", BdcSpec.CELL, cell.jsonObject.keys)
            }
        }
    }

    @Test
    fun latitudeAndLongitudeAreStringsWithSixDecimalPlaces() {
        // §5.1.7 asks for at least 6 decimal digits and allows numeric strings, which keep
        // trailing zeros. These are its examples: 38.8860976, -76.9962846 and "38.886090".
        val example = exportedLocation(lat = 38.8860976, lon = -76.9962846)
        assertEquals(JsonPrimitive("38.886098"), example["latitude"])
        assertEquals(JsonPrimitive("-76.996285"), example["longitude"])

        val trailingZeros = exportedLocation(lat = 38.88609, lon = -76.9963)
        assertEquals(JsonPrimitive("38.886090"), trailingZeros["latitude"])
        assertEquals(JsonPrimitive("-76.996300"), trailingZeros["longitude"])
    }

    @Test
    fun latitudeAndLongitudeUseThePhonesDecimalSeparatorAsReleased() {
        // On a phone whose language writes decimals with a comma, the coordinates aren't decimal
        // degrees (§5.1.7). The server's FCC payload sends locations.lat and locations.lon as JSON
        // numbers, so only this export is affected.
        Locale.setDefault(Locale.GERMANY)

        val location = exportedLocation(lat = 38.8860976, lon = -76.9962846)
        assertEquals(JsonPrimitive("38,886098"), location["latitude"])
        assertEquals(JsonPrimitive("-76,996285"), location["longitude"])
    }

    @Test
    fun timestampsAreUtcWithSeconds() {
        // §5.1.2 to §5.1.8.1 ask for ISO-8601 times with seconds and a time zone offset, in the
        // pattern YYYY-MM-DD[T]hh:mm:ss±hh:mm. The export writes UTC with ISO-8601's Z as the
        // offset, and fractions of a second when the time has them. The server's FCC payload uses
        // Postgres's JSON form of the same times, which writes the offset as ±hh:mm.
        val export = export()
        val download = export.obj("tests").obj("download")

        assertEquals(JsonPrimitive("2024-09-06T21:33:36Z"), export["server_timestamp"])
        assertEquals(JsonPrimitive("2024-09-06T21:33:20Z"), download["timestamp"])
        assertEquals(JsonPrimitive("2024-09-06T21:33:20Z"), download.array("locations")[0].jsonObject["timestamp"])
        assertEquals(JsonPrimitive("2024-09-06T21:33:21Z"), download.array("cells")[1].jsonObject["timestamp"])

        val upload = TestRecords.measurement("upload").copy(timestamp = Instant.parse("2024-09-06T21:33:28.250Z"))
        assertEquals(JsonPrimitive("2024-09-06T21:33:28.250Z"), exportedTest(upload)["timestamp"])
    }

    @Test
    fun throughputTestsReportBytesAndRateAsIntegers() {
        // §5.1.4's examples: warmup_duration in microseconds, byte counts, and bytes_sec, which the
        // export rounds from the measured rate (21573627.6 bytes a second).
        val download = exportedTest(TestRecords.measurement("download"))

        assertEquals(JsonPrimitive(2_997_000), download["warmup_duration"])
        assertEquals(JsonPrimitive(22_970_368), download["warmup_bytes_transferred"])
        assertEquals(JsonPrimitive(107_995_136), download["bytes_transferred"])
        assertEquals(JsonPrimitive(21_573_628), download["bytes_sec"])
        assertEquals(JsonArray(listOf(JsonPrimitive(TestRecords.TARGET))), download["targets"])
    }

    @Test
    fun throughputDurationIncludesTheWarmupAsReleased() {
        // §5.1.4 gives duration as the time the test metric took, apart from warmup_duration, and
        // its example bytes_sec (21573628) is close to bytes_transferred / duration (107995136
        // bytes in 5.005 s), which only holds for the active time. MeasurementTest records a
        // throughput measurement's duration as warmup plus active time, and the export writes
        // that. The server's FCC payload sends upload_download_data.duration, the active time.
        val download = TestRecords.measurement("download")
        assertEquals(2_997_000L + 5_005_000L, download.duration)
        assertEquals(5_005_000L, download.uploadDownloadData!!.duration)

        assertEquals(JsonPrimitive(8_002_000), exportedTest(download)["duration"])
    }

    @Test
    fun latencyTestReportsRoundTripTimeJitterAndPackets() {
        // §5.1.6's examples: duration, round_trip_time and jitter in microseconds, which is how
        // LatencyData holds them (the history screen divides rtt by 1000 to show milliseconds),
        // and the packet counts.
        val latency = exportedTest(TestRecords.measurement("latency"))

        assertEquals(JsonPrimitive(2_490_000), latency["duration"])
        assertEquals(JsonPrimitive(30_000), latency["round_trip_time"])
        assertEquals(JsonPrimitive(8_000), latency["jitter"])
        assertEquals(JsonPrimitive(200), latency["packets_sent"])
        assertEquals(JsonPrimitive(199), latency["packets_received"])
        assertEquals(JsonArray(listOf(JsonPrimitive(TestRecords.TARGET))), latency["targets"])
    }

    @Test
    fun connectionTypeUsesTheSpecsCodes() {
        // §5.1.4 to §5.1.6: cell, wifi or other.
        assertEquals(JsonPrimitive("cell"), exportedConnectionType(NetworkConnectionType.CELLULAR))
        assertEquals(JsonPrimitive("wifi"), exportedConnectionType(NetworkConnectionType.WIFI))
        assertEquals(JsonPrimitive("other"), exportedConnectionType(NetworkConnectionType.NONE))
    }

    @Test
    fun unknownConnectionTypeIsOtherAsReleased() {
        // §5.1.4 to §5.1.6: a null connection_type is treated as cell. The export writes "other"
        // for a measurement with no connection type, while the server's FCC payload has no
        // connection_type at all, so the FCC takes the same test as cell.
        assertEquals(JsonPrimitive("other"), exportedConnectionType(null))
    }

    @Test
    fun flagsAreTheMeasurements() {
        // §5.1.4 to §5.1.6.
        val m = TestRecords.measurement("upload")
        val upload = exportedTest(m)

        assertEquals(JsonPrimitive(m.success), upload["success_flag"])
        assertEquals(JsonPrimitive(m.carrierAggregation), upload["carrier_aggregation_flag"])
        assertEquals(JsonPrimitive(m.networkConnected), upload["network_connected_flag"])
        assertEquals(JsonPrimitive(m.networkAvailable), upload["network_available_flag"])
        assertEquals(JsonPrimitive(m.networkRoaming), upload["network_roaming_flag"])
    }

    @Test
    fun everyCellValueIsExportedUnderItsSpecName() {
        // §5.1.8.1. The LTE cell holds the section's examples.
        val (lte, nr) = exportedTest(TestRecords.measurement("latency")).array("cells").map { it.jsonObject }

        assertEquals(
            buildJsonObject {
                put("timestamp", "2024-09-06T21:33:14Z")
                put("cell_id", 10_283_271)
                put("physical_cell_id", 90)
                put("cell_connection", 1) // primary serving
                put("network_generation", "4G")
                put("network_subtype", "LTE")
                put("signal_strength", -96) // the RSRP, for LTE
                put("rssi", -63)
                put("rsrp", -96)
                put("rsrq", -11)
                put("sinr", 1)
                put("csi_rsrp", JsonNull) // must be null for LTE
                put("csi_rsrq", JsonNull)
                put("csi_sinr", JsonNull)
                put("cqi", 11)
                put("spectrum_band", "2")
                put("spectrum_bandwidth", 15f)
                put("arfcn", 675)
            },
            lte,
        )
        assertEquals(
            buildJsonObject {
                put("timestamp", "2024-09-06T21:33:15Z")
                put("cell_id", 5_001_234_567)
                put("physical_cell_id", 412)
                put("cell_connection", 2) // secondary serving
                put("network_generation", "5G")
                put("network_subtype", "NRNSA")
                put("signal_strength", -101)
                put("rssi", JsonNull) // may be null for 5G
                put("rsrp", -102)
                put("rsrq", -12)
                put("sinr", 7)
                put("csi_rsrp", -104)
                put("csi_rsrq", -13)
                put("csi_sinr", 5)
                put("cqi", 9)
                put("spectrum_band", "n2")
                put("spectrum_bandwidth", JsonNull)
                put("arfcn", 386_000)
            },
            nr,
        )
    }

    @Test
    fun locationsAndCellsWithoutATimestampAreLeftOut() {
        // §5.1.7 and §5.1.8.1 require a timestamp on each location and cell.
        val m = TestRecords.measurement("latency")
        val exported = exportedTest(
            m.copy(
                locations = m.locations!! + m.locations!![0].copy(id = "no-time", timestamp = null),
                cells = m.cells!! + m.cells!![0].copy(id = "no-time", timestamp = null),
            )
        )

        assertEquals(2, exported.array("locations").size)
        assertEquals(2, exported.array("cells").size)
    }

    @Test
    fun blankBandAndUnknownBandwidthAreNull() {
        // §5.1.8.1 lets spectrum_band and spectrum_bandwidth be null when the phone has no valid
        // value. Android reports an unknown LTE bandwidth as CellInfo.UNAVAILABLE kHz, and
        // TelephonyInfoManager divides it by 1000 before checking for that, so it's saved as
        // 2147483 MHz. The export writes null for that and for a blank band. (The server's FCC
        // payload sends cells.spectrum_band and spectrum_bandwidth as they were saved.)
        val m = TestRecords.measurement("download")
        val lte = m.cells!![0]
        val unknown = listOf(
            lte.copy(spectrumBand = "", spectrumBandwidth = 2147483f),
            lte.copy(spectrumBand = " ", spectrumBandwidth = null),
        )

        for (cell in exportedTest(m.copy(cells = unknown)).array("cells")) {
            assertEquals(JsonNull, cell.jsonObject["spectrum_band"])
            assertEquals(JsonNull, cell.jsonObject["spectrum_bandwidth"])
        }
    }

    @Test
    fun submissionFieldsComeFromTheTestAndItsSubmission() {
        // §5.1.2. test_id (FccExportExtensionsTest), environment_code and device_timestamp have
        // tests of their own.
        val ownTests = setOf("test_id", "environment_code", "device_timestamp", "tests")
        val export = JsonObject(export().filterKeys { it !in ownTests })

        assertEquals(
            buildJsonObject {
                put("device_id", "908cdafa-7fbe-4f9d-8ea4-03595ed6bba8")
                put("manufacturer", "Google")
                put("model", "Pixel 8")
                put("device_type", "Android") // iOS, Android or Other
                put("operating_system", "Android 14")
                put("device_imei", "490154203237518")
                put("device_tac", "35871196")
                put("app_name", "CellWatch")
                put("app_version", "1.1.0")
                put("sim_mobile_country_code", "310")
                put("sim_mobile_network_code", "260")
                put("net_mobile_country_code", "311")
                put("net_mobile_network_code", "480")
                put("provider_name", "acme wireless")
                put("scheduled_test_flag", false)
                put("in_vehicle_flag", true)
                put("external_antenna_flag", false)
                put("server_source_ip_address", "172.58.245.221")
                put("server_source_port", 55758)
                put("server_timestamp", "2024-09-06T21:33:36Z")
            },
            export,
        )
    }

    @Test
    fun environmentCodeIsSetBesideTheInVehicleFlagAsReleased() {
        // §5.1.2: environment_code must be null when in_vehicle_flag isn't. The export writes both,
        // with 1 (in-vehicle mobile) or 0 (outdoor stationary). The server's FCC payload sends
        // in_vehicle_flag alone.
        val inVehicle = export()
        assertEquals(JsonPrimitive(true), inVehicle["in_vehicle_flag"])
        assertEquals(JsonPrimitive(1), inVehicle["environment_code"])

        val outdoors = export(TestRecords.group().copy(submission = TestRecords.submission().copy(inVehicle = false)))
        assertEquals(JsonPrimitive(false), outdoors["in_vehicle_flag"])
        assertEquals(JsonPrimitive(0), outdoors["environment_code"])
    }

    @Test
    fun deviceTimestampIsTheLatencyTestsStartAsReleased() {
        // §5.1.2: device_timestamp is when the phone sent the test to the app's servers, by the
        // phone's clock. The export writes the latency test's start. The server's FCC payload sends
        // fcc_submissions.device_timestamp, which MeasurementManager sets when it creates the
        // submission after the upload test.
        val group = TestRecords.group()
        val exported = export(group)["device_timestamp"]

        assertEquals(JsonPrimitive(group.latency!!.timestamp.toString()), exported)
        assertNotEquals(JsonPrimitive(group.submission!!.deviceTimestamp.toString()), exported)
    }

    @Test
    fun withoutALatencyMeasurementTheDownloadDescribesTheDevice() {
        val download = TestRecords.measurement("download").copy(deviceModel = "Pixel 8 Pro")
        val export = export(TestRecords.group().copy(latency = null, download = download))

        assertEquals(JsonPrimitive("Pixel 8 Pro"), export["model"])
        assertEquals("§5.1.3's latency test", JsonNull, export.obj("tests")["latency"])
    }

    private fun export(group: MeasurementGroup = TestRecords.group()) =
        exportJson.encodeToJsonElement(group.toFccSubmissionExport()!!).jsonObject

    private fun exportedTest(m: Measurement): JsonObject = when (m.type) {
        "latency" -> exportJson.encodeToJsonElement(m.toSubmissionTestLatency()!!)
        else -> exportJson.encodeToJsonElement(m.toUDSubmissionTest()!!)
    }.jsonObject

    private fun exportedLocation(lat: Double, lon: Double): JsonObject {
        val m = TestRecords.measurement("latency")
        val location = m.locations!![0].copy(lat = lat, lon = lon)
        return exportedTest(m.copy(locations = listOf(location))).array("locations").single().jsonObject
    }

    private fun exportedConnectionType(type: NetworkConnectionType?) =
        exportedTest(TestRecords.measurement("download").copy(connectionType = type))["connection_type"]

    private fun JsonObject.obj(key: String) = getValue(key).jsonObject

    private fun JsonObject.array(key: String) = getValue(key).jsonArray
}
