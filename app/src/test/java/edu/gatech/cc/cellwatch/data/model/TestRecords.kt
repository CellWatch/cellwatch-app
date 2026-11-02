package edu.gatech.cc.cellwatch.data.model

import edu.gatech.cc.cellwatch.data.network.model.NetworkMeasurementWithData
import edu.gatech.cc.cellwatch.domain.telephony.managers.NetworkConnectionType
import kotlinx.datetime.Instant
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.put
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * One test as the phone saves it, with every field filled in, for tests that check each field is
 * carried from one form to the next. Neighbouring fields of the same type hold different values,
 * so passing one in the other's place shows up.
 *
 * Values are the examples from the FCC's mobile speed test data spec (see [BdcSpec]) where it has
 * one, with its US Eastern (-04:00) times written in UTC. The submission's server-side fields
 * (submitted, submittedOn, submission, submissionResponse, createdOn, updatedOn) keep their
 * defaults, as on the phone.
 */
internal object TestRecords {
    const val GROUP_ID = "3f2c9a8e-1b4d-4c6e-9f00-a1b2c3d4e5f6"

    /** §5.1.4's targets example. */
    const val TARGET = "s1-fcc-north-carolina.mozark.ai"

    /** §5.1.2's device_id example. */
    private const val DEVICE_ID = "908cdafa-7fbe-4f9d-8ea4-03595ed6bba8"

    // §5.1.6's, §5.1.4's and §5.1.5's timestamp examples: 17:33:14, 17:33:20 and 17:33:28 at -04:00.
    private val starts = mapOf(
        "latency" to Instant.parse("2024-09-06T21:33:14Z"),
        "download" to Instant.parse("2024-09-06T21:33:20Z"),
        "upload" to Instant.parse("2024-09-06T21:33:28Z"),
    )

    // Microseconds: §5.1.6's latency duration example, and for download and upload §5.1.4's and
    // §5.1.5's warmup_duration plus duration, as MeasurementTest records a throughput measurement's
    // duration as its warmup plus active time.
    private val durations = mapOf(
        "latency" to 2_490_000L,
        "download" to 2_997_000L + 5_005_000L,
        "upload" to 3_688_000L + 5_343_000L,
    )

    fun group(groupId: String = GROUP_ID) = MeasurementGroup(
        latency = measurement("latency", groupId),
        download = measurement("download", groupId),
        upload = measurement("upload", groupId),
        submission = submission(groupId),
    )

    /** A "latency", "download" or "upload" measurement. */
    fun measurement(type: String, groupId: String = GROUP_ID): Measurement {
        val id = "$groupId-$type"
        val start = starts.getValue(type)
        val duration = durations.getValue(type)

        return Measurement(
            id = id,
            groupId = groupId,
            campaignId = "campaign-$type",
            sessionId = "session-$type",
            deviceId = DEVICE_ID,
            deviceManufacturer = "Google",
            deviceModel = "Pixel 8",
            deviceOsName = "Android",
            deviceOsVersion = "14",
            appName = "CellWatch",
            // §5.1.2's provider_name example, lowercased as TelephonyInfoManager.getProviderName does.
            provider = "acme wireless",
            type = type,
            timestamp = start,
            duration = duration,
            scheduled = false,
            success = true,
            carrierAggregation = false,
            networkConnected = true,
            networkAvailable = false,
            networkRoaming = true,
            simMcc = "310",
            simMnc = "260",
            netMcc = "311",
            netMnc = "480",
            extraData = "extra-$type",
            createdOn = start + 1.minutes,
            updatedOn = start + 2.minutes,
            uploadDownloadData = if (type == "latency") null else uploadDownloadData(type, id, start),
            latencyData = if (type == "latency") latencyData(id, start) else null,
            locations = locations(id, start, duration),
            cells = cells(id, start),
            connectionType = NetworkConnectionType.CELLULAR,
            cellularDataEnabled = true,
            uploadTime = start + 3.minutes,
            appVersion = "1.1.0",
        )
    }

    /** The FCC submission for a test, as MeasurementManager makes it and the uploader completes it. */
    fun submission(groupId: String = GROUP_ID) = FccSubmission(
        id = groupId,
        // Never set on the phone; fcc_submissions has no column for it (SupabaseSchemaContractTest).
        challengeDataId = null,
        // §5.1.1's examples.
        contactName = "Jane Broadband",
        contactEmail = "jane.broadband@fcc.gov",
        contactPhone = "888-225-5322",
        // §5.1.2's examples for the times, source address and port, TAC, device ID, maker and model.
        deviceTimestamp = Instant.parse("2024-09-06T21:33:38Z"),
        serverTimestamp = Instant.parse("2024-09-06T21:33:36Z"),
        sourceIp = "172.58.245.221",
        sourcePort = 55758,
        deviceId = DEVICE_ID,
        deviceImei = "490154203237518",
        deviceTac = "35871196",
        deviceType = "Android",
        deviceManufacturer = "Google",
        deviceModel = "Pixel 8",
        deviceOsName = "Android 14",
        appName = "CellWatch",
        appVersion = "1.1.0",
        provider = "acme wireless",
        simCountryCode = "310",
        simNetworkCode = "260",
        netCountryCode = "311",
        netNetworkCode = "480",
        inVehicle = true,
        externalAntenna = false,
        uploadTime = Instant.parse("2024-09-06T21:40:00Z"),
    )

    /** The arguments NetworkMeasurementDatasource.insertMeasurement sends to insert_measurement. */
    fun insertMeasurementArguments(m: Measurement) = NetworkMeasurementWithData(
        m.asNetworkModel(),
        m.uploadDownloadData?.asNetworkModel(),
        m.latencyData?.asNetworkModel(),
        m.locations?.map { it.asNetworkModel() },
        m.cells?.map { it.asNetworkModel() },
    )

    private fun uploadDownloadData(type: String, measurementId: String, start: Instant) =
        if (type == "download") {
            // §5.1.4's examples; the spec has no application byte counts.
            UploadDownloadData(
                id = "$measurementId-data",
                measurementId = measurementId,
                warmupDuration = 2_997_000,
                warmupBytes = 22_970_368,
                duration = 5_005_000,
                bytes = 107_995_136,
                bytesPerSec = 21_573_627.6,
                applicationBytes = 107_413_504,
                applicationBytesPerSec = 21_461_239.5,
                servers = listOf(TARGET),
                createdOn = start + 1.minutes,
                updatedOn = start + 2.minutes,
            )
        } else {
            // §5.1.5's examples.
            UploadDownloadData(
                id = "$measurementId-data",
                measurementId = measurementId,
                warmupDuration = 3_688_000,
                warmupBytes = 7_864_320,
                duration = 5_343_000,
                bytes = 14_157_824,
                bytesPerSec = 2_649_671.8,
                applicationBytes = 13_959_168,
                applicationBytesPerSec = 2_612_514.1,
                servers = listOf(TARGET),
                createdOn = start + 1.minutes,
                updatedOn = start + 2.minutes,
            )
        }

    // §5.1.6's examples, but with one packet lost so the two counts differ.
    private fun latencyData(measurementId: String, start: Instant) = LatencyData(
        id = "$measurementId-data",
        measurementId = measurementId,
        rtt = 30_000,
        jitter = 8_000,
        sent = 200,
        received = 199,
        servers = listOf(TARGET),
        createdOn = start + 1.minutes,
        updatedOn = start + 2.minutes,
    )

    // Fixes at the start and end of the test, as §5.1 asks for at least two. The first uses
    // §5.1.7's examples.
    private fun locations(measurementId: String, start: Instant, duration: Long) = listOf(
        Location(
            id = "$measurementId-location-1",
            timestamp = start,
            lat = 38.8860976,
            lon = -76.9962846,
            accuracy = 12.3484,
            speed = 0.0,
            speedAccuracy = 20.0,
            heading = 271.5,
            measurementId = measurementId,
            createdOn = start + 1.minutes,
            updatedOn = start + 2.minutes,
        ),
        Location(
            id = "$measurementId-location-2",
            timestamp = start + duration.microseconds,
            lat = 38.8861234,
            lon = -76.9960011,
            accuracy = 9.75,
            speed = 1.25,
            speedAccuracy = 3.5,
            heading = 268.0,
            measurementId = measurementId,
            createdOn = start + 1.minutes,
            updatedOn = start + 2.minutes,
        ),
    )

    // The serving LTE cell, with §5.1.8.1's examples, and a secondary 5G cell. Between them every
    // field is set: the CSI fields are 5G only, and the app records no RSSI or bandwidth for 5G.
    private fun cells(measurementId: String, start: Instant) = listOf(
        Cell(
            id = "$measurementId-cell-1",
            timestamp = start,
            cellId = 10_283_271,
            physicalCellId = 90,
            cellConnection = 1,
            networkGeneration = "4G",
            networkSubtype = "LTE",
            signalStrength = -96,
            rssi = -63,
            rsrp = -96,
            rsrq = -11,
            sinr = 1,
            cqi = 11,
            spectrumBand = "2",
            spectrumBandwidth = 15f,
            arfcn = 675,
            measurementId = measurementId,
            createdOn = start + 1.minutes,
            updatedOn = start + 2.minutes,
        ),
        Cell(
            id = "$measurementId-cell-2",
            timestamp = start + 1.seconds,
            cellId = 5_001_234_567,
            physicalCellId = 412,
            cellConnection = 2,
            networkGeneration = "5G",
            networkSubtype = "NRNSA",
            signalStrength = -101,
            rsrp = -102,
            rsrq = -12,
            sinr = 7,
            csiRsrp = -104,
            csiRsrq = -13,
            csiSinr = 5,
            cqi = 9,
            spectrumBand = "n2",
            arfcn = 386_000,
            measurementId = measurementId,
            createdOn = start + 1.minutes,
            updatedOn = start + 2.minutes,
        ),
    )
}

/**
 * Puts [value] under [key] the way Json.Default encodes a field, leaving it out when null (with
 * encodeDefaults = false, a null that is the field's default isn't written).
 */
internal fun JsonObjectBuilder.putIfSet(key: String, value: Any?) {
    when (value) {
        null -> Unit
        is String -> put(key, value)
        is Number -> put(key, value)
        is Boolean -> put(key, value)
        is Instant -> put(key, value.toString())
        is Enum<*> -> put(key, value.name)
        is List<*> -> put(key, JsonArray(value.map { JsonPrimitive(it as String) }))
        else -> throw IllegalArgumentException("no JSON form for $value")
    }
}
