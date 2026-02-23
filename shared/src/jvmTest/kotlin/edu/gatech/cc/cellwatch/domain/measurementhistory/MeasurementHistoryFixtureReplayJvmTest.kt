package edu.gatech.cc.cellwatch.domain.measurementhistory

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MeasurementHistoryFixtureReplayJvmTest {
    @Test
    fun replayFixture_matchesExpectedReadModels_andMsakVersion() {
        val fixtureFile = MeasurementHistoryFixtureSupport.defaultFixtureFile()
        val fixture = MeasurementHistoryFixtureSupport.load(fixtureFile)

        assertEquals(1, fixture.metadata.fixtureSchemaVersion)
        assertEquals(
            MeasurementHistoryFixtureSupport.expectedMsakClientKmpVersion(),
            fixture.metadata.msakClientKmpVersion,
            "Fixture msak-client-kmp version mismatch. Re-capture fixture after dependency updates.",
        )
        assertTrue(fixture.scenarios.isNotEmpty())

        val useCase = MeasurementHistoryStatusUseCase()
        fixture.scenarios.forEach { scenario ->
            val model = useCase.present(MeasurementHistoryFixtureSupport.toInput(scenario))
            assertEquals(scenario.expectedStateKey, model.stateKey.name, "scenario=${scenario.name}")
            assertEquals(scenario.expectedSyncStateKey, model.syncStateKey.name, "scenario=${scenario.name}")
            assertEquals(scenario.expectedTitle, model.title, "scenario=${scenario.name}")
            assertEquals(scenario.expectedLatencyText, model.latencyText, "scenario=${scenario.name}")
            assertEquals(scenario.expectedDownloadText, model.downloadText, "scenario=${scenario.name}")
            assertEquals(scenario.expectedUploadText, model.uploadText, "scenario=${scenario.name}")
            assertEquals(scenario.expectedUploadedText, model.uploadedText, "scenario=${scenario.name}")
            assertEquals(scenario.expectedSyncSummary, model.syncSummary, "scenario=${scenario.name}")
        }
    }
}
