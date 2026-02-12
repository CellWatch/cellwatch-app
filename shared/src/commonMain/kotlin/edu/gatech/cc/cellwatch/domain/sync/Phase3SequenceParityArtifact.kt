package edu.gatech.cc.cellwatch.domain.sync

import edu.gatech.cc.cellwatch.domain.fcc.MeasurementSequenceHarnessResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class Phase3SequenceParityArtifact(
    val schemaVersion: Int = 1,
    val platform: String,
    val suite: String = "phase3-sequence-local-msak",
    val throughputMachine: String,
    val latencyMachine: String,
    val submissionCreated: Boolean,
    val persistedMeasurements: Int,
    val persistedSubmissions: Int,
    val persistedMeasurementsWithCapabilitySupport: Int,
    val persistedMeasurementsWithCapabilityNotes: Int,
    val capabilityPersistenceSummary: String,
)

class Phase3SequenceParityArtifactFactory {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = false
    }

    fun build(platform: String, result: MeasurementSequenceHarnessResult): Phase3SequenceParityArtifact {
        return Phase3SequenceParityArtifact(
            platform = platform.lowercase(),
            throughputMachine = result.throughputMachine,
            latencyMachine = result.latencyMachine,
            submissionCreated = result.submissionCreated,
            persistedMeasurements = result.persistedMeasurements,
            persistedSubmissions = result.persistedSubmissions,
            persistedMeasurementsWithCapabilitySupport = result.persistedMeasurementsWithCapabilitySupport,
            persistedMeasurementsWithCapabilityNotes = result.persistedMeasurementsWithCapabilityNotes,
            capabilityPersistenceSummary = result.capabilityPersistenceSummary,
        )
    }

    fun buildJson(platform: String, result: MeasurementSequenceHarnessResult): String {
        return json.encodeToString(build(platform, result))
    }

    fun parseJson(jsonText: String): Phase3SequenceParityArtifact {
        return json.decodeFromString(jsonText)
    }
}

object Phase3SequenceParityArtifactChecker {
    fun keyFieldMismatches(
        left: Phase3SequenceParityArtifact,
        right: Phase3SequenceParityArtifact,
    ): List<String> {
        val mismatches = mutableListOf<String>()
        if (left.schemaVersion != right.schemaVersion) mismatches += "schemaVersion"
        if (left.suite != right.suite) mismatches += "suite"
        if (left.submissionCreated != right.submissionCreated) mismatches += "submissionCreated"
        if (left.persistedMeasurements != right.persistedMeasurements) mismatches += "persistedMeasurements"
        if (left.persistedSubmissions != right.persistedSubmissions) mismatches += "persistedSubmissions"
        if (left.persistedMeasurementsWithCapabilitySupport != right.persistedMeasurementsWithCapabilitySupport) {
            mismatches += "persistedMeasurementsWithCapabilitySupport"
        }
        if (left.persistedMeasurementsWithCapabilityNotes != right.persistedMeasurementsWithCapabilityNotes) {
            mismatches += "persistedMeasurementsWithCapabilityNotes"
        }
        if (left.capabilityPersistenceSummary != right.capabilityPersistenceSummary) {
            mismatches += "capabilityPersistenceSummary"
        }
        return mismatches
    }

    fun machineIdErrors(artifact: Phase3SequenceParityArtifact): List<String> {
        val errors = mutableListOf<String>()
        if (artifact.throughputMachine.isBlank()) errors += "throughputMachineBlank"
        if (artifact.latencyMachine.isBlank()) errors += "latencyMachineBlank"
        return errors
    }
}
