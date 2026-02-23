package edu.gatech.cc.cellwatch.domain.measurementrun

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MeasurementRunFixtureReplayJvmTest {
    @Test
    fun replayFixture_matchesExpectedStateTransitions_andMsakVersion() {
        val fixtureFile = MeasurementRunFixtureSupport.defaultFixtureFile()
        val fixture = MeasurementRunFixtureSupport.load(fixtureFile)

        assertEquals(1, fixture.metadata.fixtureSchemaVersion)
        assertEquals(
            MeasurementRunFixtureSupport.expectedMsakClientKmpVersion(),
            fixture.metadata.msakClientKmpVersion,
            "Fixture msak-client-kmp version mismatch. Re-capture fixture after dependency updates.",
        )
        assertTrue(fixture.steps.isNotEmpty())

        val controller = MeasurementRunViewController()
        val presenter = MeasurementRunUiPresenter()
        val groupId = "fixture-group-1"

        fixture.steps.forEach { step ->
            val state = MeasurementRunFixtureSupport.applyStep(
                controller = controller,
                step = step,
                groupId = groupId,
            )
            val ui = presenter.present(state)
            assertEquals(step.expectedProgress, state.progress.name, "event=${step.event}")
            assertEquals(step.expectedHeaderKey, ui.headerKey.name, "event=${step.event}")
        }

        val finalState = controller.currentState()
        assertEquals(MeasurementRunProgress.END, finalState.progress)
        assertEquals("latency", finalState.results?.latency?.type)
        assertEquals("download", finalState.results?.download?.type)
        assertEquals("upload", finalState.results?.upload?.type)
    }
}
