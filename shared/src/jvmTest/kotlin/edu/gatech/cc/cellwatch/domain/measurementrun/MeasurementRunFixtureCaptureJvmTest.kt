package edu.gatech.cc.cellwatch.domain.measurementrun

import kotlinx.datetime.Clock
import java.io.File
import kotlin.test.Test

class MeasurementRunFixtureCaptureJvmTest {
    @Test
    fun captureFixture_whenEnabled_writesFixtureJson() {
        if (System.getenv("CELLWATCH_CAPTURE_MEASUREMENT_FIXTURE") != "1") {
            return
        }

        val output = System.getenv("CELLWATCH_MEASUREMENT_FIXTURE_OUT")
            ?.takeIf { it.isNotBlank() }
            ?.let(::File)
            ?: MeasurementRunFixtureSupport.defaultFixtureFile()

        val controller = MeasurementRunViewController()
        val presenter = MeasurementRunUiPresenter()
        val groupId = "fixture-group-1"

        val events = listOf(
            "SET_IN_VEHICLE_FALSE",
            "SEQUENCE_STARTED",
            "LOCATE_STARTED",
            "LATENCY_STARTED",
            "LATENCY_MEASURED",
            "DOWNLOAD_STARTED",
            "DOWNLOAD_MEASURED",
            "UPLOAD_STARTED",
            "UPLOAD_MEASURED",
            "COMPLETED_SUCCESS",
        )

        val steps = events.map { event ->
            val state = MeasurementRunFixtureSupport.applyStep(
                controller = controller,
                step = MeasurementRunFixtureStep(event = event, expectedProgress = "", expectedHeaderKey = ""),
                groupId = groupId,
            )
            val ui = presenter.present(state)
            MeasurementRunFixtureStep(
                event = event,
                expectedProgress = state.progress.name,
                expectedHeaderKey = ui.headerKey.name,
            )
        }

        val fixture = MeasurementRunFixture(
            metadata = MeasurementRunFixtureMetadata(
                fixtureSchemaVersion = 1,
                source = "measurement-run-controller-capture",
                capturedAtUtc = Clock.System.now().toString(),
                msakClientKmpVersion = MeasurementRunFixtureSupport.expectedMsakClientKmpVersion(),
            ),
            steps = steps,
        )

        MeasurementRunFixtureSupport.save(output, fixture)
    }
}
