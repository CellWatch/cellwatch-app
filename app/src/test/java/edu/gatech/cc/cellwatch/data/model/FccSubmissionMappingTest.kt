package edu.gatech.cc.cellwatch.data.model

import edu.gatech.cc.cellwatch.data.local.model.asExternalModel
import edu.gatech.cc.cellwatch.data.network.model.asExternalModel
import kotlinx.datetime.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.encodeToJsonElement
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * An FCC submission goes into Room when the test ends. The uploader reads it back, adds the
 * server's view of the connection, and inserts it into fcc_submissions, and the server posts it to
 * the FCC from that row (private.fcc_submission_payload) as the Submission Object of §5.1.2 of the
 * FCC's mobile speed test data spec (July 28, 2025). The mappers pass their fields by position, so
 * these check that no field is dropped or lands in another.
 */
class FccSubmissionMappingTest {
    // With the fields only the server fills in, when it posts the test to the FCC.
    private val posted = TestRecords.submission().copy(
        submitted = true,
        submittedOn = Instant.parse("2024-09-06T21:45:00Z"),
        submission = """{"test_id":"${TestRecords.GROUP_ID}"}""",
        submissionResponse = "accepted",
        createdOn = Instant.parse("2024-09-06T21:40:01Z"),
        updatedOn = Instant.parse("2024-09-06T21:45:01Z"),
    )

    @Test
    fun roomKeepsEverythingThePhoneRecords() {
        val s = TestRecords.submission()

        assertEquals(s, s.asEntity().asExternalModel())
    }

    @Test
    fun serverSideFieldsAreNotReadBackFromRoom() {
        // Room stores them, but FccSubmissionEntity.asExternalModel stops after externalAntenna, so
        // they come back as their defaults. MeasurementManager never sets them, so nothing is lost
        // today; a value the phone did set would be dropped on the next read.
        val entity = posted.asEntity()
        assertEquals(true, entity.submitted)
        assertEquals(posted.submittedOn, entity.submittedOn)
        assertEquals(posted.submission, entity.submission)
        assertEquals(posted.submissionResponse, entity.submissionResponse)
        assertEquals(posted.createdOn, entity.createdOn)
        assertEquals(posted.updatedOn, entity.updatedOn)

        assertEquals(TestRecords.submission(), entity.asExternalModel())
    }

    @Test
    fun uploadSendsEveryValueUnderItsColumn() {
        // NetworkMeasurementDatasource.insertFccSubmission inserts this. supabase-kt 1.0.3 encodes it
        // with Json.Default, which leaves out submitted = false, its default.
        val s = TestRecords.submission()

        assertEquals(
            buildJsonObject {
                putIfSet("id", s.id)
                putIfSet("contact_name", s.contactName)
                putIfSet("contact_email", s.contactEmail)
                putIfSet("contact_phone", s.contactPhone)
                putIfSet("device_timestamp", s.deviceTimestamp)
                putIfSet("server_timestamp", s.serverTimestamp)
                putIfSet("source_ip", s.sourceIp)
                putIfSet("source_port", s.sourcePort)
                putIfSet("device_id", s.deviceId)
                putIfSet("device_imei", s.deviceImei)
                putIfSet("device_tac", s.deviceTac)
                putIfSet("device_type", s.deviceType)
                putIfSet("device_manufacturer", s.deviceManufacturer)
                putIfSet("device_model", s.deviceModel)
                putIfSet("device_os", s.deviceOsName)
                putIfSet("app_name", s.appName)
                putIfSet("app_version", s.appVersion)
                putIfSet("provider", s.provider)
                putIfSet("sim_country_code", s.simCountryCode)
                putIfSet("sim_network_code", s.simNetworkCode)
                putIfSet("net_country_code", s.netCountryCode)
                putIfSet("net_network_code", s.netNetworkCode)
                putIfSet("in_vehicle", s.inVehicle)
                putIfSet("external_antenna", s.externalAntenna)
            },
            Json.encodeToJsonElement(s.asNetworkModel()),
        )
    }

    @Test
    fun serverReplyReadsBackAsTheSameSubmission() {
        // insertFccSubmission reads the inserted row back like this. The upload time is the
        // phone's own and isn't sent.
        assertEquals(posted.copy(uploadTime = null), posted.asNetworkModel().asExternalModel())
    }
}
