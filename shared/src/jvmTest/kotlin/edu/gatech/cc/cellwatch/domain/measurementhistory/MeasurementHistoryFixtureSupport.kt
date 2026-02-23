package edu.gatech.cc.cellwatch.domain.measurementhistory

import edu.gatech.cc.cellwatch.domain.model.LatencyData
import edu.gatech.cc.cellwatch.domain.model.Measurement
import edu.gatech.cc.cellwatch.domain.model.UploadDownloadData
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class MeasurementHistoryFixture(
    val metadata: MeasurementHistoryFixtureMetadata,
    val scenarios: List<MeasurementHistoryFixtureScenario>,
)

@Serializable
data class MeasurementHistoryFixtureMetadata(
    val fixtureSchemaVersion: Int,
    val source: String,
    val capturedAtUtc: String,
    val msakClientKmpVersion: String,
)

@Serializable
data class MeasurementHistoryFixtureScenario(
    val name: String,
    val pendingMeasurements: Int? = null,
    val pendingSubmissions: Int? = null,
    val recentMeasurements: List<MeasurementHistoryFixtureMeasurement> = emptyList(),
    val fallbackLatencyText: String? = null,
    val fallbackDownloadText: String? = null,
    val fallbackUploadText: String? = null,
    val fallbackUploadedText: String? = null,
    val fallbackDetailText: String? = null,
    val expectedStateKey: String,
    val expectedSyncStateKey: String,
    val expectedTitle: String,
    val expectedLatencyText: String,
    val expectedDownloadText: String,
    val expectedUploadText: String,
    val expectedUploadedText: String,
    val expectedSyncSummary: String,
)

@Serializable
data class MeasurementHistoryFixtureMeasurement(
    val id: String,
    val groupId: String,
    val type: String,
    val timestampIso: String,
    val latencyMicros: Long? = null,
    val bytesPerSec: Double? = null,
    val uploadTimeIso: String? = null,
)

object MeasurementHistoryFixtureSupport {
    val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = false
    }

    fun expectedMsakClientKmpVersion(): String {
        val version = System.getProperty("cellwatch.msakClientKmpVersion")?.trim().orEmpty()
        require(version.isNotEmpty()) {
            "Missing system property cellwatch.msakClientKmpVersion. Configure Test tasks in Gradle."
        }
        return version
    }

    fun defaultFixtureFile(): File {
        val moduleRelative = File("src/commonTest/resources/fixtures/measurement-history/msak-history-v1.json")
        if (moduleRelative.exists()) return moduleRelative
        return File("shared/src/commonTest/resources/fixtures/measurement-history/msak-history-v1.json")
    }

    fun load(file: File): MeasurementHistoryFixture {
        return json.decodeFromString(MeasurementHistoryFixture.serializer(), file.readText())
    }

    fun save(file: File, fixture: MeasurementHistoryFixture) {
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(MeasurementHistoryFixture.serializer(), fixture))
    }

    fun toInput(scenario: MeasurementHistoryFixtureScenario): MeasurementHistoryStatusInput {
        return MeasurementHistoryStatusInput(
            recentMeasurements = scenario.recentMeasurements.map { row ->
                Measurement(
                    id = row.id,
                    groupId = row.groupId,
                    type = row.type,
                    timestamp = Instant.parse(row.timestampIso),
                    latencyData = row.latencyMicros?.let { LatencyData(rtt = it.toInt()) },
                    uploadDownloadData = row.bytesPerSec?.let { UploadDownloadData(bytesPerSec = it) },
                    uploadTime = row.uploadTimeIso?.let(Instant::parse),
                )
            },
            pendingMeasurements = scenario.pendingMeasurements,
            pendingSubmissions = scenario.pendingSubmissions,
            fallbackLatencyText = scenario.fallbackLatencyText,
            fallbackDownloadText = scenario.fallbackDownloadText,
            fallbackUploadText = scenario.fallbackUploadText,
            fallbackUploadedText = scenario.fallbackUploadedText,
            fallbackDetailText = scenario.fallbackDetailText,
        )
    }
}
