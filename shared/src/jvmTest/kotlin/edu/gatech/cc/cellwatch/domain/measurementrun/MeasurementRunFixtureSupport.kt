package edu.gatech.cc.cellwatch.domain.measurementrun

import edu.gatech.cc.cellwatch.domain.model.LatencyData
import edu.gatech.cc.cellwatch.domain.model.Measurement
import edu.gatech.cc.cellwatch.domain.model.UploadDownloadData
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class MeasurementRunFixture(
    val metadata: MeasurementRunFixtureMetadata,
    val steps: List<MeasurementRunFixtureStep>,
)

@Serializable
data class MeasurementRunFixtureMetadata(
    val fixtureSchemaVersion: Int,
    val source: String,
    val capturedAtUtc: String,
    val msakClientKmpVersion: String,
)

@Serializable
data class MeasurementRunFixtureStep(
    val event: String,
    val expectedProgress: String,
    val expectedHeaderKey: String,
)

object MeasurementRunFixtureSupport {
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
        val moduleRelative = File("src/commonTest/resources/fixtures/measurement-run/msak-sequence-v1.json")
        if (moduleRelative.exists()) return moduleRelative
        return File("shared/src/commonTest/resources/fixtures/measurement-run/msak-sequence-v1.json")
    }

    fun load(file: File): MeasurementRunFixture {
        return json.decodeFromString(MeasurementRunFixture.serializer(), file.readText())
    }

    fun save(file: File, fixture: MeasurementRunFixture) {
        file.parentFile?.mkdirs()
        file.writeText(json.encodeToString(MeasurementRunFixture.serializer(), fixture))
    }

    fun applyStep(
        controller: MeasurementRunViewController,
        step: MeasurementRunFixtureStep,
        groupId: String,
    ): MeasurementRunState {
        return when (step.event) {
            "SET_IN_VEHICLE_FALSE" -> controller.setInVehicle(false)
            "SEQUENCE_STARTED" -> controller.onSequenceStarted(groupId)
            "LOCATE_STARTED" -> controller.onLocateStarted()
            "LATENCY_STARTED" -> controller.onLatencyStarted()
            "LATENCY_MEASURED" -> controller.onLatencyMeasured(
                Measurement(
                    groupId = groupId,
                    type = "latency",
                    latencyData = LatencyData(rtt = 42_000),
                ),
            )

            "DOWNLOAD_STARTED" -> controller.onDownloadStarted()
            "DOWNLOAD_MEASURED" -> controller.onThroughputMeasured(
                Measurement(
                    groupId = groupId,
                    type = "download",
                    uploadDownloadData = UploadDownloadData(bytes = 25_000_000, duration = 1_000_000),
                ),
            )

            "UPLOAD_STARTED" -> controller.onUploadStarted()
            "UPLOAD_MEASURED" -> controller.onThroughputMeasured(
                Measurement(
                    groupId = groupId,
                    type = "upload",
                    uploadDownloadData = UploadDownloadData(bytes = 10_000_000, duration = 1_000_000),
                ),
            )

            "COMPLETED_SUCCESS" -> controller.onCompleted(
                group = controller.currentState().results,
                errorCode = null,
                errorText = null,
            )

            else -> error("Unsupported fixture event: ${step.event}")
        }
    }
}
