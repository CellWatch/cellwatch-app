package edu.gatech.cc.cellwatch.data.network

import edu.gatech.cc.cellwatch.data.model.BdcSpec
import edu.gatech.cc.cellwatch.data.model.TestRecords
import edu.gatech.cc.cellwatch.data.model.asNetworkModel
import edu.gatech.cc.cellwatch.data.network.model.NetworkCell
import edu.gatech.cc.cellwatch.data.network.model.NetworkFccSubmission
import edu.gatech.cc.cellwatch.data.network.model.NetworkLatencyData
import edu.gatech.cc.cellwatch.data.network.model.NetworkLocation
import edu.gatech.cc.cellwatch.data.network.model.NetworkMeasurement
import edu.gatech.cc.cellwatch.data.network.model.NetworkUploadDownloadData
import edu.gatech.cc.cellwatch.domain.telephony.managers.NetworkConnectionType
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

/**
 * Checks what the phone sends Supabase against supabase/full_schema.sql, the snapshot of the live
 * schema (refreshed with supabase/sync_from_remote.sh). supabase-kt 1.0.3 encodes inserts and rpc
 * arguments with Json.Default, which leaves out fields that are null by default.
 *
 * A key with no column breaks the upload or loses data. PostgREST rejects an insert into
 * fcc_submissions that names a column the table doesn't have. insert_measurement takes table rows,
 * which PostgREST builds from the JSON using only the keys that match a column, so a misnamed key
 * is dropped without an error. Either way, a new field needs its migration live, and this snapshot
 * refreshed, before the app release that sends it.
 */
class SupabaseSchemaContractTest {
    private val schema = Schema.load()

    @Test
    fun everySubmissionFieldButChallengeDataIdHasAColumn() {
        // fcc_submissions has no challenge_data_id. The phone never sets it, and Json.Default leaves
        // out a null default, so it's never sent; setting it would make every insert fail.
        assertEquals(
            setOf("challenge_data_id"),
            fieldNames(NetworkFccSubmission.serializer()) - schema.columns("fcc_submissions"),
        )
    }

    @Test
    fun submissionThePhoneSendsNamesOnlyColumns() {
        val sent = Json.encodeToJsonElement(TestRecords.submission().asNetworkModel()).jsonObject.keys

        assertEquals(emptySet<String>(), sent - schema.columns("fcc_submissions"))
        assertFalse("challenge_data_id" in sent)
    }

    @Test
    fun everyMeasurementFieldHasAColumn() {
        // The data, latency data, locations and cells go in insert_measurement's other arguments;
        // asNetworkModel leaves these four null, so Json.Default leaves them out.
        assertEquals(
            setOf("upload_download_data", "latency_data", "locations", "cells"),
            fieldNames(NetworkMeasurement.serializer()) - schema.columns("measurements"),
        )
        assertNoColumnMissing("upload_download_data", fieldNames(NetworkUploadDownloadData.serializer()))
        assertNoColumnMissing("latency_data", fieldNames(NetworkLatencyData.serializer()))
        assertNoColumnMissing("locations", fieldNames(NetworkLocation.serializer()))
        assertNoColumnMissing("cells", fieldNames(NetworkCell.serializer()))
    }

    @Test
    fun insertMeasurementArgumentsAreTheFunctionsParameters() {
        // PostgREST finds the function by its argument names.
        for (type in listOf("latency", "download", "upload")) {
            assertEquals(type, schema.parameters("insert_measurement"), sent(type).keys)
        }
    }

    @Test
    fun measurementThePhoneSendsNamesOnlyColumns() {
        for (type in listOf("latency", "download", "upload")) {
            val sent = sent(type)

            assertNoColumnMissing("measurements", sent.getValue("in_measurement").jsonObject.keys)
            sent.getValue("in_measurement_data").takeIf { it != JsonNull }?.let {
                assertNoColumnMissing("upload_download_data", it.jsonObject.keys)
            }
            sent.getValue("in_latency_data").takeIf { it != JsonNull }?.let {
                assertNoColumnMissing("latency_data", it.jsonObject.keys)
            }
            for (location in sent.getValue("in_locations").jsonArray) {
                assertNoColumnMissing("locations", location.jsonObject.keys)
            }
            for (cell in sent.getValue("in_cells").jsonArray) {
                assertNoColumnMissing("cells", cell.jsonObject.keys)
            }
        }
    }

    @Test
    fun connectionTypesAreTheDatabaseEnumsLabels() {
        val sent = NetworkConnectionType.values().map { Json.encodeToJsonElement(it).jsonPrimitive.content }

        assertEquals(schema.enumLabels("connection_type"), sent.toSet())
    }

    @Test
    fun cellsCarryEveryFieldOfTheSpecsCellObject() {
        // fcc_submission_payload sends each test's cells to the FCC under their column names, as the
        // Cell Objects of §5.1.8.1 of the FCC's mobile speed test data spec (July 28, 2025).
        assertEquals(emptySet<String>(), BdcSpec.CELL - fieldNames(NetworkCell.serializer()))
        assertEquals(emptySet<String>(), BdcSpec.CELL - schema.columns("cells"))
    }

    private fun sent(type: String): JsonObject =
        Json.encodeToJsonElement(TestRecords.insertMeasurementArguments(TestRecords.measurement(type))).jsonObject

    private fun assertNoColumnMissing(table: String, keys: Set<String>) =
        assertEquals("keys with no column in $table", emptySet<String>(), keys - schema.columns(table))

    private fun fieldNames(serializer: KSerializer<*>): Set<String> {
        val descriptor = serializer.descriptor
        return (0 until descriptor.elementsCount).map { descriptor.getElementName(it) }.toSet()
    }

    /** Tables, functions and types from supabase/full_schema.sql, as pg_dump writes them. */
    private class Schema(private val sql: String) {
        fun columns(table: String): Set<String> =
            Regex("""^\s+"(\w+)"""", RegexOption.MULTILINE)
                .findAll(section("CREATE TABLE IF NOT EXISTS \"public\".\"$table\" (", "\n);"))
                .map { it.groupValues[1] }
                .toSet()

        fun parameters(function: String): Set<String> =
            section("CREATE OR REPLACE FUNCTION \"public\".\"$function\"(", ") RETURNS")
                .split(", ")
                .map { Regex("""^"(\w+)"""").find(it)!!.groupValues[1] }
                .toSet()

        fun enumLabels(type: String): Set<String> =
            Regex("'([^']*)'")
                .findAll(section("CREATE TYPE \"public\".\"$type\" AS ENUM (", ");"))
                .map { it.groupValues[1] }
                .toSet()

        private fun section(start: String, end: String): String {
            val from = sql.indexOf(start)
            check(from >= 0) { "full_schema.sql has no $start" }
            return sql.substring(from + start.length, sql.indexOf(end, from))
        }

        companion object {
            // Gradle runs unit tests in app/, so look upwards from wherever they run.
            fun load(): Schema {
                var dir: File? = File(System.getProperty("user.dir")).absoluteFile
                while (dir != null) {
                    val file = File(dir, "supabase/full_schema.sql")
                    if (file.isFile) {
                        return Schema(file.readText())
                    }
                    dir = dir.parentFile
                }
                throw AssertionError("no supabase/full_schema.sql above ${System.getProperty("user.dir")}")
            }
        }
    }
}
