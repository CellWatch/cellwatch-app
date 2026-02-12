package edu.gatech.cc.cellwatch.domain.sync

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

class Phase3UiSliceFormatterTest {

    @Test
    fun format_emitsStableFieldOrder() {
        val text = Phase3UiSliceFormatter.format(
            envelopeText = "smokeEnvelope scenario=phase3-sequence-sync status=SUCCESS",
            result = Phase3UiSliceResult(
                groupId = "group-1",
                throughputMachine = "t-machine",
                latencyMachine = "l-machine",
                submissionCreated = true,
                mapStartMeasurementsUploaded = 1,
                mapStartSubmissionsUploaded = 1,
                measurementCompleteUploadTimeSet = true,
                persistedMeasurements = 3,
                persistedSubmissions = 1,
                capabilityPersistenceSummary = "capabilityPersistence(total=3, supportStates=3, notes=3)",
                capabilitySummary = "capabilities(t=PARTIAL,n=PARTIAL,l=NOT_SUPPORTED,d=AVAILABLE)",
            ),
        )

        assertContains(text, "smokeEnvelope scenario=phase3-sequence-sync status=SUCCESS")
        assertContains(text, "group=group-1")
        assertContains(text, "throughput=t-machine")
        assertContains(text, "latency=l-machine")
        assertContains(text, "mapStartUploaded(m=1,s=1)")
        assertContains(text, "measurementCompleteUploadTimeSet=true")
        assertContains(text, "persistedMeasurements=3, persistedSubmissions=1")
        assertTrue(text.trimEnd().endsWith("capabilities(t=PARTIAL,n=PARTIAL,l=NOT_SUPPORTED,d=AVAILABLE)"))
    }
}
