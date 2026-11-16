package edu.gatech.cc.cellwatch.data.network

import edu.gatech.cc.cellwatch.data.model.TestRecords
import edu.gatech.cc.cellwatch.data.model.asNetworkModel
import edu.gatech.cc.cellwatch.data.network.model.NetworkFccSubmission
import edu.gatech.cc.cellwatch.data.network.model.NetworkMeasurement
import io.github.jan.supabase.postgrest.query.PostgrestResult
import io.ktor.http.Headers
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Supabase answers each upload with the row it saved, and the phone reads that row back. A column
 * added to fcc_submissions or measurements after a build is installed mustn't break its uploads
 * (#186). The rows here are what the phone sends, as Supabase would return them with one column
 * more.
 */
class ServerRepliesTest {
    @Test
    fun aSavedSubmissionReadsBackWithAColumnTheAppDoesntKnow() {
        val sent = TestRecords.submission().asNetworkModel()

        assertEquals(sent, insertReply(sent).savedSubmission())
    }

    @Test
    fun aSavedMeasurementReadsBackWithAColumnTheAppDoesntKnow() {
        for (type in listOf("latency", "download", "upload")) {
            val sent = TestRecords.measurement(type).asNetworkModel()

            assertEquals(type, sent, rpcReply(sent).savedMeasurement())
        }
    }

    @Test
    fun supabaseKtsDefaultRejectsAColumnTheAppDoesntKnow() {
        // How release 13 reads these replies, and why the app passes its own Json.
        val submission = insertReply(TestRecords.submission().asNetworkModel())
        val measurement = rpcReply(TestRecords.measurement("latency").asNetworkModel())

        assertThrows(SerializationException::class.java) { submission.decodeSingle<NetworkFccSubmission>() }
        assertThrows(SerializationException::class.java) { measurement.decodeAs<NetworkMeasurement>() }
    }

    /** An insert answers with the rows it saved. */
    private inline fun <reified T> insertReply(row: T) = reply(JsonArray(listOf(withANewColumn(row))))

    /** insert_measurement answers with the one measurements row it saved. */
    private inline fun <reified T> rpcReply(row: T) = reply(withANewColumn(row))

    private fun reply(body: JsonElement) = PostgrestResult(body, Headers.Empty)

    // supabase-kt sends rows encoded with Json.Default, as here.
    private inline fun <reified T> withANewColumn(row: T) =
        JsonObject(Json.encodeToJsonElement(row).jsonObject + ("added_later" to JsonPrimitive("a value")))
}
