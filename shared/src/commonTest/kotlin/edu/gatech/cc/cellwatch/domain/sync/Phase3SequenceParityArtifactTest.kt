package edu.gatech.cc.cellwatch.domain.sync

import edu.gatech.cc.cellwatch.domain.fcc.MeasurementSequenceHarnessResult
import kotlin.test.Test
import kotlin.test.assertTrue

class Phase3SequenceParityArtifactTest {

    @Test
    fun schemaAndKeyFieldParity_androidVsIosArtifacts_match() {
        val factory = Phase3SequenceParityArtifactFactory()
        val base = MeasurementSequenceHarnessResult(
            throughputMachine = "machine-a",
            latencyMachine = "machine-b",
            groupId = "group-1",
            submissionCreated = true,
            persistedMeasurements = 3,
            persistedSubmissions = 1,
            persistedMeasurementsWithCapabilitySupport = 3,
            persistedMeasurementsWithCapabilityNotes = 3,
            capabilityPersistenceSummary = "capabilityPersistence(total=3, supportStates=3, notes=3)",
            capabilitySummary = "capabilities(t=PARTIAL,n=PARTIAL,l=NOT_SUPPORTED,d=AVAILABLE)",
        )
        val android = factory.build(platform = "android", result = base)
        val ios = factory.build(platform = "ios", result = base.copy(
            throughputMachine = "machine-c",
            latencyMachine = "machine-d",
        ))

        val mismatches = Phase3SequenceParityArtifactChecker.keyFieldMismatches(android, ios)
        val androidMachineErrors = Phase3SequenceParityArtifactChecker.machineIdErrors(android)
        val iosMachineErrors = Phase3SequenceParityArtifactChecker.machineIdErrors(ios)

        assertTrue(mismatches.isEmpty(), "Expected no key mismatches, got: $mismatches")
        assertTrue(androidMachineErrors.isEmpty(), "Expected android machine ids, got: $androidMachineErrors")
        assertTrue(iosMachineErrors.isEmpty(), "Expected ios machine ids, got: $iosMachineErrors")
    }
}
