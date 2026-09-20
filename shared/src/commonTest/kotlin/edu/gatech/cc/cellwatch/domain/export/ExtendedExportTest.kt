package edu.gatech.cc.cellwatch.domain.export

import edu.gatech.cc.cellwatch.domain.fcc.FccSubmissionOutcomeMessage
import edu.gatech.cc.cellwatch.domain.model.FccSubmission
import edu.gatech.cc.cellwatch.domain.model.LatencyData
import edu.gatech.cc.cellwatch.domain.model.Measurement
import edu.gatech.cc.cellwatch.domain.model.MeasurementGroup
import edu.gatech.cc.cellwatch.domain.model.NetworkConnectionType
import edu.gatech.cc.cellwatch.domain.model.UploadDownloadData
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The export may end up in front of the FCC, so what it claims about a run
 * has to follow from what was stored, not from a plausible guess.
 */
class ExtendedExportTest {

    private val now = Instant.parse("2026-09-20T12:00:00Z")

    private fun measurement(
        type: String,
        connection: NetworkConnectionType? = NetworkConnectionType.CELLULAR,
        provider: String? = "Test Carrier",
    ) = Measurement(
        id = "m-$type",
        groupId = "g1",
        type = type,
        timestamp = now,
        success = true,
        connectionType = connection,
        provider = provider,
        latencyData = if (type == "latency") {
            LatencyData(id = "l1", measurementId = "m-$type", rtt = 1000, jitter = 10)
        } else {
            null
        },
        uploadDownloadData = if (type != "latency") {
            UploadDownloadData(id = "u-$type", measurementId = "m-$type", bytes = 100, bytesPerSec = 10.0)
        } else {
            null
        },
    )

    private fun group(
        connection: NetworkConnectionType? = NetworkConnectionType.CELLULAR,
        provider: String? = "Test Carrier",
        submission: FccSubmission? = null,
    ) = MeasurementGroup(
        latency = measurement("latency", connection, provider),
        download = measurement("download", connection, provider),
        upload = measurement("upload", connection, provider),
        submission = submission,
        id = "g1",
    )

    @Test
    fun `a submitted run says so`() {
        val outcome = group(submission = FccSubmission(id = "g1")).deriveFccOutcome(challengeMode = true)
        assertEquals(FccSubmissionOutcomeMessage.SUBMITTED, outcome)
    }

    @Test
    fun `opting out wins over any eligibility reason`() {
        val outcome = group(connection = NetworkConnectionType.WIFI).deriveFccOutcome(challengeMode = false)
        assertEquals(FccSubmissionOutcomeMessage.OPTED_OUT, outcome)
    }

    @Test
    fun `a non-cellular run names the connection`() {
        val outcome = group(connection = NetworkConnectionType.WIFI).deriveFccOutcome(challengeMode = true)
        assertEquals(FccSubmissionOutcomeMessage.NOT_ELIGIBLE, outcome)
    }

    @Test
    fun `a cellular run with no carrier names the carrier`() {
        // The case an emulator produces, and the one that was previously
        // mislabelled as a connection problem.
        val outcome = group(provider = null).deriveFccOutcome(challengeMode = true)
        assertEquals(FccSubmissionOutcomeMessage.CARRIER_UNKNOWN, outcome)
    }

    @Test
    fun `an otherwise-eligible run admits the reason is unrecorded`() {
        val outcome = group().deriveFccOutcome(challengeMode = true)
        assertEquals(FccSubmissionOutcomeMessage.REASON_UNRECORDED, outcome)
    }
}
